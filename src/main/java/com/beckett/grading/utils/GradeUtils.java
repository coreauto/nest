package com.beckett.grading.utils;

import com.beckett.order.entity.CardSuborderItem;
import com.beckett.order.entity.CardSuborderItemPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.beckett.grading.utils.Constants.*;

public class GradeUtils {

    private GradeUtils() {}

    public static boolean isValidGradeVal(Double grade) {

        if(grade == null) return true;

        // Check if grade is within the acceptable range
        if (grade < 1.0 || grade > 10.0) {
            return false;
        }

        // Check if grade is in increments of 0.5
        double remainder = grade % 0.5;
        return remainder == 0.0;
    }

    public static Map<String, String> prepareLines(CardSuborderItem cardSuborderItem) {
        Map<String, String> param = new HashMap<>();
        param.put(ITEM_ATTR_NAME, "");   //if item_attr_name
        param.put("card_key", cardSuborderItem.getCardNumber()); //card_number
        if(cardSuborderItem.getPlayers() != null && !cardSuborderItem.getPlayers().isEmpty()) {
            param.put(PLAYER_NAME, cardSuborderItem.getPlayers().stream().map(CardSuborderItemPlayer::getName).collect(Collectors.joining(",")));         // Player names comman seperated
        } else {
            param.put(PLAYER_NAME,"");
        }
        param.put(SET_NAME, cardSuborderItem.getSetsName()); //setname
        // hard-coding it currently since in phase 1, only BGS is supported.
        String service = "bccg"; // for bgs and for bccg is "bccg"
        Map<String, String> result = generate(param, service);
        Map<String, String> line = new HashMap<>();
        line.put("line1", result.getOrDefault("1", "").toUpperCase());
        line.put("line2", result.getOrDefault("2", "").toUpperCase());
        line.put("line3", result.getOrDefault("3", "").toUpperCase());
        line.put("line4", result.getOrDefault("4", "").toUpperCase());
        return line;
    }

    private static Map<String, String> generate(Map<String, String> item, String serviceType) { //NOSONAR
        String missingAttrib = "";
        if (!item.get(ITEM_ATTR_NAME).isEmpty()) {
            missingAttrib = " (" + item.get(ITEM_ATTR_NAME) + ")";
        }
        String playerName = "#" + item.get("card_key") + " " + item.get(PLAYER_NAME) + missingAttrib;

        if (serviceType.equals("bccg")) {
            item.put(SET_NAME, item.get(SET_NAME) + " " + playerName + missingAttrib);
            playerName = "";
        }

        Map<String, String> word = new HashMap<>();
        String setName = item.get(SET_NAME);
        int i = 1;

        if (setName.length() > 32) {
            String arrWord;
            while (setName.length() > 32) { //NOSONAR
                arrWord = setName.substring(0, 32);
                int lastSpace = arrWord.lastIndexOf(' ');
                if (lastSpace > 0) {
                    arrWord = setName.substring(0, lastSpace);
                    word.put(String.valueOf(i), arrWord);
                    setName = setName.replaceFirst(arrWord, "").trim();
                } else {
                    break; //NOSONAR
                }
                i++;
                if (i > 3) {
                    break; //NOSONAR
                }
            }
            if (!setName.isEmpty()) {
                word.put(String.valueOf(i), setName);
                i++;
            }
        } else {
            word.put(String.valueOf(i), setName);
            i++;
        }

        if (playerName.length() > 35) {
            while (playerName.length() > 35) {
                String arrWord = playerName.substring(0, 21);
                int lastSpace = arrWord.lastIndexOf(' ');
                if (lastSpace > 0) {
                    arrWord = playerName.substring(0, lastSpace);
                    word.put(String.valueOf(i), arrWord);
                    playerName = playerName.replaceFirst(arrWord, "").trim();
                } else {
                    break;
                }
                i++;
            }
            if (!playerName.isEmpty()) {
                word.put(String.valueOf(i), playerName);
                i++;//NOSONAR
            }
        } else {
            word.put(String.valueOf(i), playerName);
            i++; //NOSONAR
        }

        return word;
    }
}