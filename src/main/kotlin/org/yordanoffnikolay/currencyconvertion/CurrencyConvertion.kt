package org.yordanoffnikolay.currencyconvertion

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileReader
import java.util.*
import java.util.concurrent.ConcurrentHashMap


fun main(args: Array<String>) {
    val apiKey = loadApiKey()
    val client = OkHttpClient()

    if (args.isEmpty() || !validateDate(args[0])) {
        println("Please provide the date in format 'YYYY-MM-DD'")
        return
    }

    val date = args[0]
    println("Please enter the desired amount up to 2 decimal places or 'END' to exit")

    while (true) {
        var input = readln().trim().uppercase(Locale.getDefault())
        if (input == "END") break
        while (true) {
            if (!validateAmount(input) || input.toDouble() < 0.01) {
                println("Please enter a valid amount")
                input = readln().trim().uppercase(Locale.getDefault())
                continue
            }
            break
        }
        val amount: Double = input.toDouble()

        println("Please enter base currency in format AAA")
        var baseCurrency: String
        while (true) {
            baseCurrency = readln().trim().uppercase(Locale.getDefault())
            if (!validateCurrency(baseCurrency)) {
                println("Please enter a valid currency in format AAA")
                continue
            }
            break
        }

        println("Please enter target currency in format BBB")
        var targetCurrency: String
        while (true) {
            targetCurrency = readln().trim().uppercase(Locale.getDefault())
            if (!validateCurrency(targetCurrency)) {
                println("Please enter a valid currency in format BBB")
                continue
            } else if (baseCurrency == targetCurrency) {
                println("target currency cannot be the same as base currency")
                continue
            }
            break
        }

        val exchangeRate = getExchangeRate(client, apiKey, date, baseCurrency, targetCurrency)
        val convertedAmount = amount * exchangeRate
        println(String.format("%.2f %s is %.2f %s", amount, baseCurrency, convertedAmount, targetCurrency))

        saveConversionToJSON(date, baseCurrency, targetCurrency, amount, convertedAmount)
        println()
        println("Please enter the desired amount up to 2 decimal places or 'END' to exit")
    }
}


fun loadApiKey(): String {
    val fileReader = FileReader("config.json")
    val jsonString = fileReader.readText()
    val jsonObject = JSONObject(jsonString)
    return jsonObject.getString("api_key")
}

fun validateDate(date: String): Boolean {
    return date.matches("\\d{4}-\\d\\d-\\d\\d".toRegex())
}

fun validateAmount(amount: String): Boolean {
    return amount.matches("\\d+(\\.\\d{1,2})?".toRegex())
}

fun validateCurrency(currency: String): Boolean {
    return currency.matches(Currency.getAvailableCurrencies().joinToString("|") { it.currencyCode }.toRegex())
}

fun getExchangeRate(
    client: OkHttpClient,
    apiKey: String,
    date: String,
    baseCurrency: String,
    targetCurrency: String
): Double {
    val cacheKey = "$date-$baseCurrency"
    if (exchangeRateCache.containsKey(cacheKey)) {
        return exchangeRateCache[cacheKey]!![date]!!.getDouble(targetCurrency)
    }

    val request = Request.Builder()
        .url("https://api.fastforex.io/fetch-all?from=$baseCurrency&api_key=$apiKey")
        .get()
        .addHeader("accept", "application/json")
        .build()

    val response = client.newCall(request).execute()

    val jsonObject = JSONObject(response.body!!.string())
    val exchangeRate = jsonObject.getJSONObject("results")
    exchangeRateCache.computeIfAbsent(cacheKey) { ConcurrentHashMap() }[date] = exchangeRate
    return exchangeRate.getDouble(targetCurrency)
}

fun saveConversionToJSON(
    date: String,
    baseCurrency: String,
    targetCurrency: String,
    amount: Double,
    convertedAmount: Double
) {
    val file = File("conversions.json")
    val jsonArray: JSONArray

    if (file.exists()) {
        val content = file.readText()
        jsonArray = if (content.isEmpty()) {
            JSONArray()
        } else {
            JSONArray(content)
        }
    } else {
        jsonArray = JSONArray()
    }

    val jsonObject = JSONObject().apply {
        put("date", date)
        put("amount", String.format("%.2f", amount).toDouble())
        put("base_currency", baseCurrency)
        put("target_currency", targetCurrency)
        put("converted_amount", String.format("%.2f", convertedAmount).toDouble())
    }

    jsonArray.put(jsonObject)
    file.writeText(jsonArray.toString(4))
}

val exchangeRateCache = ConcurrentHashMap<String, ConcurrentHashMap<String, JSONObject>>()