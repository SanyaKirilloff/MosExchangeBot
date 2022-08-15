package com.company;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.request.SendMessage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MoxExchangeBot {
    private final static String WELCOME_MESSAGE = "Привет! Я умею отлеживать цены на акции Мосбиржи.\n" +
            "Доступны следующие команды:\n" +
            "/price ticker - отображения цены акции.\n" +
            "/notify ticker price - добавление оповещения при достижении указанной цены на акцию" +
            " (пример /notify AFLT 17.02).\n" +
            "/list - показать список доступных акций.";

    private final static List<String> notyList = new ArrayList<>();

    public static void main(String[] args) {
        TelegramBot bot = new TelegramBot("5454256096:AAHtBjjEvwyPpT7dYICc0ytlZx2YjjrOpE4");

        Runnable runnable = () -> {
            try {
                notifyIf(bot);
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();

        bot.setUpdatesListener(updates -> {
            updates.forEach(upd -> {
                try {
                    System.out.println(upd);
                    long chatId = upd.message().chat().id();
                    String incomeMessage = upd.message().text();
                    String[] params = incomeMessage.split("\s");

                    String result;
                    switch (params[0]) {
                        case "/start"-> result = WELCOME_MESSAGE;
                        case "/list" -> result = getAllTickers();
                        case "/price" -> result = getPrice(params[1]);
                        case "/notify" -> result = addNotify(chatId, params[1], params[2]);
                        default -> result = "Пожалуйста введите корректную команду";
                    }
                    SendMessage request = new SendMessage(chatId, result);
                    bot.execute(request);
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            });
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    public static Elements getAttributesFromServer() throws IOException {
        Document doc = Jsoup.connect("https://iss.moex.com/iss/engines/stock/markets/shares/boards/TQBR/securities.xml?" +
                "iss.dp=comma&iss.meta=off&iss.only=securities&securities.columns=SECID,PREVADMITTEDQUOTE").get();
        System.out.println(doc.title());
        return doc.select("row");
    }

    public static String getAllTickers() throws IOException {
        StringBuilder result = new StringBuilder("| ");
        Elements elements = getAttributesFromServer();
        for (Element element : elements) {
            result.append(element.attr("SECID")).append(" |");
        }
        return result.toString();
    }

    public static String getPrice(String ticker) throws IOException {
        Elements elements = getAttributesFromServer();
        for (Element element : elements) {
            if (element.attr("SECID").equals(ticker)) {
                return element.attr("PREVADMITTEDQUOTE");
            }
        }
        return "Акция с таким тикером не найдена";
    }

    public static void notifyIf(TelegramBot bot) throws IOException {
        while (true) {
            try {
                Thread.sleep(3600000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            int hour = LocalDateTime.now().getHour();
            if (hour == 10) {
                for (String data : notyList) {
                    String[] params = data.split("/s");
                    double currentPrise = Double.parseDouble(getPrice(params[1]));
                    double targetPrise = Double.parseDouble(params[2]);
                    if (currentPrise >= targetPrise) {
                        SendMessage request = new SendMessage(params[0], "Акция " +  params[2] + " достигла заданной цены "
                                + params[3]);
                        bot.execute(request);
                    }
                }
            }
        }
    }

    private static String addNotify(long chatId, String ticker, String price) {
        notyList.add(chatId + " " + ticker + " " + price);
        return "Отслеживание " + ticker + " успешно добавлено";
    }
}

