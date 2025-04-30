package org.example;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.SimpleTokenizer;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    private static final Pattern ippPattern = Pattern.compile("((?:\\d{1,3}\\.){3}\\d{1,3}):(\\d{1,5})\\b");
    private static final Pattern ipPattern = Pattern.compile("(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern urlPattern = Pattern.compile("https?://[\\w\\.-]+(/[\\S]*)?");
    private static final Pattern portPattern = Pattern.compile("(?:\\bport\\s*(\\d{1,5})\\b|\\b(\\d{1,5})\\s*port\\b)", Pattern.CASE_INSENSITIVE);
    private static class TokenTag {
        String token;
        String tag;

        TokenTag(String token, String tag) {
            this.token = token;
            this.tag = tag;
        }
    }

    public static void main(String[] args) throws Exception {
        while (true) {

            Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
            System.out.println("Enter your sentence (English):");
            String input = scanner.nextLine();

            // Step 1: Detect and record special tokens and their spans
            Map<Integer, String> offsetToTag = new TreeMap<>();
            Map<Integer, String> fullSpanMap = new TreeMap<>();

            Matcher ippMatcher = ippPattern.matcher(input);
            while (ippMatcher.find()) {
                offsetToTag.put(ippMatcher.start(), "IPP");
                fullSpanMap.put(ippMatcher.start(), ippMatcher.group());
            }
            Matcher urlMatcher = urlPattern.matcher(input);
            while (urlMatcher.find()) {
                offsetToTag.put(urlMatcher.start(), "URL");
                fullSpanMap.put(urlMatcher.start(), urlMatcher.group());
            }
            Matcher portMatcher = portPattern.matcher(input);
            while (portMatcher.find()) {
                offsetToTag.put(portMatcher.start(), "PORT");
                fullSpanMap.put(portMatcher.start(), portMatcher.group());
            }
            Matcher ipMatcher = ipPattern.matcher(input);
            while (ipMatcher.find()) {
                if (!offsetToTag.containsKey(ipMatcher.start())) {
                    offsetToTag.put(ipMatcher.start(), "IP");
                    fullSpanMap.put(ipMatcher.start(), ipMatcher.group());
                }
            }

            // Step 2: Tokenize with OpenNLP
            String[] tokens = SimpleTokenizer.INSTANCE.tokenize(input);
            InputStream modelIn = Main.class.getResourceAsStream("/en-pos-maxent.bin");
            POSModel model = new POSModel(modelIn);
            POSTaggerME tagger = new POSTaggerME(model);
            String[] posTags = tagger.tag(tokens);

            // Step 3: Match tokens back to original string offsets and merge consecutive ones into special spans
            List<TokenTag> result = new ArrayList<>();
            int cursor = 0;
            while (cursor < input.length() && Character.isWhitespace(input.charAt(cursor))) cursor++;

            for (int i = 0; i < tokens.length;) {
                String token = tokens[i];
                int start = input.indexOf(token, cursor);
                if (offsetToTag.containsKey(start)) {
                    String type = offsetToTag.get(start);
                    String fullSpan = fullSpanMap.get(start);
                    result.add(new TokenTag(fullSpan, "<" + type + ">"));

                    // skip all tokens within that span
                    int spanEnd = start + fullSpan.length();
                    while (i < tokens.length && input.indexOf(tokens[i], cursor) < spanEnd) {
                        cursor = input.indexOf(tokens[i], cursor) + tokens[i].length();
                        i++;
                    }
                } else {
                    String tag = posTags[i];
                    result.add(new TokenTag(token, tag));
                    cursor = start + token.length();
                    i++;
                }
            }

            // Step 4: Output
            System.out.println("===[Tagged Output]===");
            for (TokenTag tt : result) {
                System.out.printf("[%s]-(%s) ", tt.token, tt.tag);
            }
            System.out.println();
            System.out.println("=====================");
        }
    }
}