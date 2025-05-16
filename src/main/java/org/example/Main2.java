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

    private static final Map<String, String> intentCorpus = Map.of(
            "create_firewall_rule", String.join(" ",
                    "request firewall policy", "request firewall access", "request new firewall policy",
                    "create firewall policy user", "request firewall rule", "process request new policy",
                    "help request firewall rule", "request firewall policy source ip address <IP>",
                    "request rule blocks <IP>:<PORT>", "request internal access firewall policy",
                    "request temporary high-priority firewall policy", "submit firewall request",
                    "create firewall policy", "open port <PORT> <IP>"
            ),
            "search_firewall_policy", String.join(" ",
                    "show current firewall policy", "search firewall rule applied user",
                    "find firewall policies related ip address <IP>", "see firewall rules place",
                    "look firewall policy server", "display existing firewall policies",
                    "search firewall rules assigned <IP>", "firewall policy applied port <PORT>",
                    "check policy blocking ip", "retrieve active firewall configuration",
                    "view firewall rules", "find policies outbound traffic",
                    "search policies high-priority tag", "current open firewall ports",
                    "show blocked ips firewall policy"
            ),
            "change_password", String.join(" ",
                    "change password", "reset login credentials", "update password",
                    "forgot password need change", "modify account password", "request password change",
                    "password needs updated", "change password account", "update user credentials",
                    "process change password", "reset admin password", "reset password user",
                    "change forgotten password", "change password security reasons", "update password",
                    "initiate password reset", "login password not working reset", "request credential change",
                    "change password associated id", "modify current password"
            )
    );

    private static double cosineSimilarity(Map<String, Double> vec1, Map<String, Double> vec2) {
        Set<String> allWords = new HashSet<>();
        allWords.addAll(vec1.keySet());
        allWords.addAll(vec2.keySet());

        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (String word : allWords) {
            double a = vec1.getOrDefault(word, 0.0);
            double b = vec2.getOrDefault(word, 0.0);
            dot += a * b;
            normA += a * a;
            normB += b * b;
        }
        return (normA == 0 || normB == 0) ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static Map<String, Double> computeTfIdfVector(List<String> tokens, Map<String, List<String>> corpusTokens) {
        Map<String, Double> tf = new HashMap<>();
        for (String token : tokens) {
            tf.put(token, tf.getOrDefault(token, 0.0) + 1);
        }
        int tokenCount = tokens.size();
        tf.replaceAll((k, v) -> v / tokenCount); // Normalize TF

        // Compute IDF
        Map<String, Double> idf = new HashMap<>();
        int docCount = corpusTokens.size();
        for (String term : tf.keySet()) {
            int df = 0;
            for (List<String> docTokens : corpusTokens.values()) {
                if (docTokens.contains(term)) df++;
            }
            idf.put(term, Math.log((double)(docCount + 1) / (df + 1))); // Smoothing
        }

        // TF-IDF = TF * IDF
        Map<String, Double> tfidf = new HashMap<>();
        for (String term : tf.keySet()) {
            tfidf.put(term, tf.get(term) * idf.getOrDefault(term, 0.0));
        }
        return tfidf;
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
//                    filtered.add(tokens[i] + " - " + tag);
                    filtered.add("<" + tag + ">" + " - " + tag);
                }
                // WP, WRB, VB*, NN* 태그 필터링 (Question, Verb (INTENT), Noun (OBJECT, FIELD) 등)
                else if (tag.matches("W(P|RB)") || tag.matches("VB.*") || tag.matches("NN.*")) {
                    filtered.add(tokens[i] + " - " + tag);
                }
            }

            for (String item : filtered) {
                System.out.print(item + ", ");
            }
            System.out.println();

            // Prepare the corpus token map
            Map<String, List<String>> corpusTokens = new HashMap<>();
            for (Map.Entry<String, String> entry : intentCorpus.entrySet()) {
                corpusTokens.put(entry.getKey(), Arrays.asList(entry.getValue().split("\\s+")));
            }

            // Compute TF-IDF for input
            Map<String, Double> inputVector = computeTfIdfVector(filtered.stream().map(s -> s.split(" - ")[0]).toList(), corpusTokens);

            System.out.println("\n--- TF-IDF Vector for User Input ---");
            inputVector.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> System.out.printf("%s: %.4f%n", e.getKey(), e.getValue()));

            System.out.println("\n--- TF-IDF Vectors for Intents ---");
            for (String intent : intentCorpus.keySet()) {
                List<String> docTokens = corpusTokens.get(intent);
                Map<String, Double> intentVector = computeTfIdfVector(docTokens, corpusTokens);

                System.out.printf("\n[%s]\n", intent);
                intentVector.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(e -> System.out.printf("%s: %.4f%n", e.getKey(), e.getValue()));

                double similarity = cosineSimilarity(inputVector, intentVector);
                System.out.printf("Cosine Similarity: %.4f%n", similarity);
            }


            // Compare with each intent
            String bestIntent = null;
            double bestScore = -1;
            for (String intent : intentCorpus.keySet()) {
                List<String> docTokens = corpusTokens.get(intent);
                Map<String, Double> intentVector = computeTfIdfVector(docTokens, corpusTokens);
                double similarity = cosineSimilarity(inputVector, intentVector);
                System.out.printf("Similarity with [%s]: %.3f%n", intent, similarity);
                if (similarity > bestScore) {
                    bestScore = similarity;
                    bestIntent = intent;
                }
            }

            System.out.println("Predicted Intent: " + (bestScore > 0.1 ? bestIntent : "UNKNOWN"));

        }
    }
}