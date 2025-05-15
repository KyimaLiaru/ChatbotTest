package org.example;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class Main2 {

    // Define regex patterns for special tokens
    private static final Map<String, Pattern> patternMap = new LinkedHashMap<>();
    static {
        patternMap.put("IPP", Pattern.compile("((?:\\d{1,3}\\.){3}\\d{1,3}):(\\d{1,5})"));
        patternMap.put("URL", Pattern.compile("https?://[\\w\\.-]+(/[\\S]*)?"));
        patternMap.put("PORT", Pattern.compile("(?:\\bport\\s*(\\d{1,5})\\b|\\b(\\d{1,5})\\s*port\\b)", Pattern.CASE_INSENSITIVE));
        patternMap.put("FIQ", Pattern.compile("\\bFIQ\\d{17}\\b"));
        patternMap.put("BWAY", Pattern.compile("\\bBWAY\\d{17}\\b"));
        patternMap.put("R", Pattern.compile("\\bR\\d{17}\\b"));
        patternMap.put("IP", Pattern.compile("(?:\\d{1,3}\\.){3}\\d{1,3}"));
    }

    public static void main(String[] args) throws IOException {
        // Infinite Loop for Input Testing
        while (true) {
            Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
            System.out.println("==============================");
            System.out.println("Enter your sentence (English):");
            System.out.println("------------------------------");
            String input = scanner.nextLine();

            // Step 1: Replace special tokens with placeholders and keep mapping
            Map<String, String> placeholderMap = new HashMap<>();
            int counter = 0;
            for (Map.Entry<String, Pattern> entry : patternMap.entrySet()) {
                Matcher matcher = entry.getValue().matcher(input);
                while (matcher.find()) {
                    String match = matcher.group();
                    if (!placeholderMap.containsValue(match)) {
                        String placeholder = "__" + entry.getKey() + "_" + counter++ + "__";
                        input = input.replace(match, placeholder);
                        placeholderMap.put(placeholder, match);
                    }
                }
            }

            // Step 2: Tokenize
            InputStream tokenModelIn = Main.class.getResourceAsStream("/en-token.bin");
            TokenizerModel tokenModel = new TokenizerModel(tokenModelIn);
            TokenizerME tokenizer = new TokenizerME(tokenModel);
            String[] tokens = tokenizer.tokenize(input);

            // Step 3: Restore placeholders back to actual tokens
            for (int i = 0; i < tokens.length; i++) {
                if (placeholderMap.containsKey(tokens[i])) {
                    tokens[i] = placeholderMap.get(tokens[i]);
                }
            }

            // Step 4: POS Tagging
            InputStream posModelIn = Main.class.getResourceAsStream("/en-pos-maxent.bin");
            POSModel posModel = new POSModel(posModelIn);
            POSTaggerME posTagger = new POSTaggerME(posModel);
            String[] tags = posTagger.tag(tokens);

            // Step 5: Replace POS tag for special tokens
            for (int i = 0; i < tokens.length; i++) {
                for (Map.Entry<String, Pattern> entry : patternMap.entrySet()) {
                    if (entry.getValue().matcher(tokens[i]).matches()) {
                        tags[i] = entry.getKey();
                        break;
                    }
                }
            }

            // Step 6: Output
            List<String> filtered = new ArrayList<>();

            for (int i = 0; i < tokens.length; i++) {
                String tag = tags[i];

                // 사용자 정의 태그 (<IPP> 등)
                if (patternMap.containsKey(tag)) {
                    filtered.add(tokens[i] + " - " + tag);
                }
                // WP, WRB, VB*, NN* 태그만 필터링
                else if (tag.matches("W(P|RB)") || tag.matches("VB.*") || tag.matches("NN.*")) {
                    filtered.add(tokens[i] + " - " + tag);
                }
            }

            for (String item : filtered) {
                System.out.print(item + ", ");
            }
            System.out.println();
        }
    }
}