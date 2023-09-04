package com.company;

import org.json.JSONObject;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {

    public static final boolean debug = false;
    public static final double tolerance = 0.07; // Lower to be more strict
    public static void main(String[] args) {
        doTest("AAPL");
    }

    public static void doTest(String stockSynmbol) {
        System.out.println("Stock Symbol: " + stockSynmbol);

        /* Finance data from yahoo website*/
        System.out.println("Retrieving from Yahoo Finance...");
        Map<Fields_Finance, Double> yahooData = getFinanceDataFromYahoo(stockSynmbol.toUpperCase());

        /* Finance data from google website */
        System.out.println("Retrieving from Google Finance...");
        Map<Fields_Finance, Double> googleData = getFinanceDataFromGoogle(stockSynmbol.toUpperCase());

        /* Finance data from api*/
        System.out.println("Retrieving from API Data...");
        Map<Fields_Finance, Double> apiData = getFinanceDataFromApi(stockSynmbol.toLowerCase());

        System.out.println("Comparing Yahoo vs Google Financial Data: ");
        for (Fields_Finance key : yahooData.keySet()) {
            if (!googleData.containsKey(key)) continue;
            assert Helper.getPercentError(yahooData.get(key), googleData.get(key)) < tolerance : key.name() + " - NOK";
            System.out.println(key.name() + (Helper.getPercentError(yahooData.get(key), googleData.get(key)) < tolerance ? " - OK" : " - NOK"));
        }

        System.out.println("Comparing Yahoo vs Api Financial Data: ");
        for (Fields_Finance key : yahooData.keySet()) {
            if (!apiData.containsKey(key)) continue;
            assert Helper.getPercentError(yahooData.get(key), apiData.get(key)) < tolerance : key.name() + " - NOK";
            System.out.println(key.name() + (Helper.getPercentError(yahooData.get(key), apiData.get(key)) < tolerance ? " - OK" : " - NOK"));
        }
    }

    public static Map<Fields_Finance, Double> getFinanceDataFromYahoo(String stockSymbol) {
        Map<Fields_Finance, Double> values = new TreeMap<>();
        try {
            Document doc = Jsoup.connect("https://finance.yahoo.com/quote/" + stockSymbol).get();
            String[] financeDesc = {"Current stock price", "Market capitalization", "PE ratio", "Dividend yield", "Earnings per Share"};
            String[] financeKeys = {"OPEN-value", "MARKET_CAP-value", "PE_RATIO-value", "DIVIDEND_AND_YIELD-value", "EPS_RATIO-value"};

            String[] financeValues = Arrays.stream(financeKeys).map(key -> getValueYahoo(doc, key)).toArray(String[]::new);

            for (int i = 0; i < financeDesc.length; i++) {
                String rawVal = financeValues[i];
                if (debug) System.out.println(financeDesc[i] + "=" + financeValues[i]);
                switch (financeDesc[i]) {
                    case "Current stock price":
                        values.put(Fields_Finance.STOCK_PRICE, Double.valueOf(rawVal));
                        break;
                    case "Market capitalization":
                        values.put(Fields_Finance.MARKET_CAPITALIZATION, Helper.getNonSuffixedValue(rawVal));
                        break;
                    case "PE ratio":
                        values.put(Fields_Finance.PE_RATIO, Double.valueOf(rawVal));
                        break;
                    case "Dividend yield":
                        String val = rawVal.substring(rawVal.indexOf("(")+1, rawVal.indexOf(")"));
                        values.put(Fields_Finance.DIVIDEND_YIELD, Helper.getNonPercentageFormValue(val));
                        break;
                    case "Earnings per Share":
                        values.put(Fields_Finance.EARNINGS_PER_SHARE, Double.valueOf(rawVal));
                        break;
                }
            }

        } catch (HttpStatusException ex) {
            //...
        } catch (IOException e) {
            e.printStackTrace();
        }
        return values;
    }

    public static Map<Fields_Finance, Double> getFinanceDataFromGoogle(String stockSymbol) {
        Map<Fields_Finance, Double> values = new TreeMap<>();
        try {
            Document doc = Jsoup.connect("https://www.google.com/finance/quote/" + stockSymbol + ":NASDAQ").get();
            String[] colKeys = {"Previous close", "Market cap", "P/E ratio", "Dividend yield"};

            String[] financeValues = Arrays.stream(colKeys).map(key -> getValueGoogle(doc, key)).toArray(String[]::new);

            for (int i = 0; i < colKeys.length; i++) {
                String rawVal = financeValues[i];
                if (debug) System.out.println(colKeys[i] + "=" + financeValues[i]);
                switch (colKeys[i]) {
                    case "Previous close":
                        String valS = rawVal;
                        if (valS.startsWith("$")) {
                            values.put(Fields_Finance.STOCK_PRICE, Double.valueOf(valS.substring(1)));
                        } else {
                            values.put(Fields_Finance.STOCK_PRICE, Double.valueOf(valS));
                        }
                        break;
                    case "Market cap":
                        String rawNoCurr = rawVal.indexOf(' ') > -1 ? rawVal.substring(0, rawVal.indexOf(' ')) : rawVal;
                        values.put(Fields_Finance.MARKET_CAPITALIZATION, Helper.getNonSuffixedValue(rawNoCurr));
                        break;
                    case "P/E ratio":
                        values.put(Fields_Finance.PE_RATIO, Double.valueOf(rawVal));
                        break;
                    case "Dividend yield":
                        String val = rawVal;
                        values.put(Fields_Finance.DIVIDEND_YIELD, Helper.getNonPercentageFormValue(val));
                        break;
                }
            }

        } catch (HttpStatusException ex) {
            //...
        } catch (IOException e) {
            e.printStackTrace();
        }
        return values;
    }

    public static Map<Fields_Finance, Double> getFinanceDataFromApi(String stockSymbol) {
        Map<Fields_Finance, Double> values = new TreeMap<>();
        try {
            Path path = Paths.get("api.txt");
            String read = Files.readAllLines(path).get(0);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest
                    .newBuilder()
                    .uri(URI.create("https://cloud.iexapis.com/stable/time-series/FUNDAMENTAL_VALUATIONS/" + stockSymbol + "?token="+ read))
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            String jsonStr = response.body();
            JSONObject jObject = new JSONObject(jsonStr.substring(1, jsonStr.length() - 1));

            String[] financeDesc = {"Market capitalization", "PE ratio", "Dividend yield", "Earnings per Share"};
            String[] colKeys = {"marketCapPeriodEnd", "pToE", "dividendYield", "incomeNetPerWabsoSplitAdjusted"};

            String[] financeValues = Arrays.stream(colKeys).map(key -> getValueJSON(jObject, key)).toArray(String[]::new);

            for (int i = 0; i < colKeys.length; i++) {
                String rawVal = financeValues[i];
                if (debug) System.out.println(financeDesc[i] + "=" + financeValues[i]);

                switch (colKeys[i]) {
                    case "marketCapPeriodEnd":
                        values.put(Fields_Finance.MARKET_CAPITALIZATION, Double.valueOf(rawVal));
                        break;
                    case "pToE":
                        values.put(Fields_Finance.PE_RATIO, Double.valueOf(rawVal));
                        break;
                    case "dividendYield":
                        values.put(Fields_Finance.DIVIDEND_YIELD, Double.valueOf(rawVal));
                        break;
                    case "incomeNetPerWabsoSplitAdjusted":
                        values.put(Fields_Finance.EARNINGS_PER_SHARE, Double.valueOf(rawVal));
                        break;
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return values;
    }

    public static String getValueYahoo(Document doc, String dataKey) {
        try {
            Elements e = doc.select("[data-test=\"" + dataKey + "\"]");
            return (e.first().text());
        }
        catch (Exception e) {
            throw e;
        }
    }

    public static String getValueGoogle(Document doc, String dataKey) {
        try {
            Elements e = doc.select("div:matchesOwn(" + dataKey +")");
            e = e.parents().first().parent().select("div > div:eq(1)");
            return (e.first().text());
        }
        catch (Exception e) {
            throw e;
        }
    }

    public static String getValueJSON(JSONObject obj, String dataKey) {
        try {
            return obj.get(dataKey).toString();
        } catch (Exception e) {
            throw e;
        }
    }

}

enum Fields_Finance {
    STOCK_PRICE,
    MARKET_CAPITALIZATION,
    PE_RATIO,
    DIVIDEND_YIELD,
    EARNINGS_PER_SHARE
}