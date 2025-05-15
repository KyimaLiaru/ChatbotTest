package org.example;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
//import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class Main {

    // Regex for Special Tokens
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

    private static class TokenTag {
        String token;
        String tag;

        TokenTag(String token, String tag) {
            this.token = token;
            this.tag = tag;
        }

        // Check if input token exists
        boolean checkToken(String tag, String token) {
            return this.tag.contains(tag) && this.token.toLowerCase().contains(token);
        }

        // Check if any token from input list exists
        boolean checkToken(String tag, List<String> token) {
            if (this.tag.contains(tag)) {
                for (String s : token) {
                    if (this.token.toLowerCase().contains(s)) return true;
                }
            }
            return false;
        }

        // Check if current chunk equals to input tag
        boolean checkTag(String tag) {
            return this.tag.contains(tag);
        }

    }

    private static class FirewallRequest {
        String srcName;
        String srcIp;
        String dstName;
        String dstIp;
        String svcName;
        String svcPort;
        String protocol;

        FirewallRequest() {
            this.srcName = "";
            this.srcIp = "";
            this.dstName = "";
            this.dstIp = "";
            this.svcName = "";
            this.svcPort = "";
            this.protocol = "";
        }

        @Override
        public String toString() {
            return "Source: " + this.srcName + " (" + this.srcIp + ") , Destination: " + this.dstName + " (" + this.dstIp + ") , Protocol: " + this.protocol + ", Port : " + this.svcName + " (" + this.svcPort + ")";
        }
    }

    // Token Sequence
    private static final Map<String, List<String>> ruleRegex = Map.of(
            "<QUESTION>", List.of("WRB( TO)?( VBP| MD)?( PRP)?( VB)?", "MD(( PRP VB( (PRP|IN)))|( PRP))"),
            "<OBJECT>", List.of("(DT )?(JJ )?(NNP|NNS|NN)( (NNP|NNS|NN))*"),
            "<FIELD>", List.of("IN( DT)?(( (NNP|NNS|NN))+)?"),
            "<INTENT>", List.of("PRP( MD)?( VBP| VB)( TO)? VB", "(PRP )?(VB|VBP|VBG)( TO| VB| VBP| VBG)*")
            // "Request" 가 맨 앞에 올 시 동사가 아닌 명사로 처리되어 INTENT 로 분류되지 않음 ,,, (예. Request a firewall policy.)
    );

    public static void main(String[] args) throws Exception {
        // Infinite Loop for Input Testing
        while (true) {
            Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
            System.out.println("Enter your sentence (English):");
            String input = scanner.nextLine();

            // Find Special Tokens First if Exists
            Map<Integer, String> offsetToTag = new TreeMap<>();
            Map<Integer, String> fullSpanMap = new TreeMap<>();

            for (Map.Entry<String, Pattern> entry : patternMap.entrySet()) {
                String label = entry.getKey();
                Pattern pattern = entry.getValue();
                Matcher matcher = pattern.matcher(input);
                while (matcher.find()) {
                    if (label.equals("IP") && offsetToTag.containsKey(matcher.start())) continue;
                    offsetToTag.put(matcher.start(), label);
                    fullSpanMap.put(matcher.start(), matcher.group());
                }
            }

            // Tokenize the Input
//            String[] tokens = SimpleTokenizer.INSTANCE.tokenize(input);
            InputStream tokenModelIn = Main.class.getResourceAsStream("/en-token.bin");
            TokenizerModel tokenModel = new TokenizerModel(tokenModelIn);
            TokenizerME tokenizer = new TokenizerME(tokenModel);
            String[] tokens = tokenizer.tokenize(input);

            InputStream modelIn = Main.class.getResourceAsStream("/en-pos-maxent.bin");
            POSModel model = new POSModel(modelIn);
            POSTaggerME tagger = new POSTaggerME(model);
            String[] posTags = tagger.tag(tokens);



            List<TokenTag> taggedTokens = new ArrayList<>();
            int cursor = 0;
            while (cursor < input.length() && Character.isWhitespace(input.charAt(cursor))) cursor++;

            for (int i = 0; i < tokens.length;) {
                String token = tokens[i];
                int start = input.indexOf(token, cursor);
                if (offsetToTag.containsKey(start)) {
                    String type = offsetToTag.get(start);
                    String fullSpan = fullSpanMap.get(start);
                    taggedTokens.add(new TokenTag(fullSpan, "<" + type + ">"));
                    int spanEnd = start + fullSpan.length();
                    while (i < tokens.length && input.indexOf(tokens[i], cursor) < spanEnd) {
                        cursor = input.indexOf(tokens[i], cursor) + tokens[i].length();
                        i++;
                    }
                } else {
                    taggedTokens.add(new TokenTag(token, posTags[i]));
                    cursor = start + token.length();
                    i++;
                }
            }

            // Analyze the Meaning Blocks of User Input Based On Token Regex
            List<TokenTag> chunks = new ArrayList<>();
            int i = 0;
            while (i < taggedTokens.size()) {
                boolean matched = false;
                for (Map.Entry<String, List<String>> entry : ruleRegex.entrySet()) {
                    String label = entry.getKey();
                    for (String regex : entry.getValue()) {
                        for (int window = 6; window >= 1; window--) {
                            if (i + window <= taggedTokens.size()) {
                                StringBuilder posSeq = new StringBuilder();
                                for (int j = 0; j < window; j++) {
                                    if (j > 0) posSeq.append(" ");
                                    posSeq.append(taggedTokens.get(i + j).tag);
                                }
                                if (posSeq.toString().matches(regex)) {
                                    StringBuilder block = new StringBuilder();
                                    for (int j = 0; j < window; j++) {
                                        if (j > 0) block.append(" ");
                                        block.append(taggedTokens.get(i + j).token);
                                    }
                                    chunks.add(new TokenTag(block.toString(), label));
                                    i += window;
                                    matched = true;
                                    break;
                                }
                            }
                        }
                        if (matched) break;
                    }
                    if (matched) break;
                }
                if (!matched) {
                    chunks.add(taggedTokens.get(i));
                    i++;
                }
            }

            // Check Meaning Block Analysis of Input -- START
            System.out.println("======[POS Tags]======");
            for (TokenTag token : taggedTokens) {
                System.out.printf("[%s]-(%s) ", token.token, token.tag);
            }
//            System.out.println("\n===[Meaning Blocks]===");
//            for (TokenTag chunk : chunks) {
//                System.out.printf("[%s]-(%s) ", chunk.token, chunk.tag);
//            }
            System.out.println("\n======================\n");
            // Check Meaning Block Analysis of Input -- END

            String intent = "";
            String object = "";
            String srcIp = "";
            String svcPort = "";

            // Checking Intent of User Input
            /*
            for (int j = 0; j < chunks.size(); j++) {
                TokenTag chunk = chunks.get(j);
                if (chunks.get(j).checkToken("INTENT", List.of("request", "submit", "create"))) {
                    intent = "request";
                    continue;
                }

                if  (chunk.checkToken("OBJECT", List.of("rule", "policy", "firewall"))) {
                    object = "firewall";
                    continue;
                }

                if (chunk.checkToken("FIELD", "source")) {
                    TokenTag chunkj = chunks.get(j+1);
                    if (chunkj.checkTag("IPP")) {
                        String[] ipp = chunkj.token.split(":");
                        srcIp = ipp[0];
                        svcPort = ipp[1];
                    }
                }
            }

            // Handling User Input
            if (intent.equals("request") & object.equals("firewall")) {
                FirewallRequest fr = new FirewallRequest();
                if (!srcIp.isEmpty()) {
                    fr.srcIp = srcIp;
                } else {
                    System.out.println("Please provide source IP:");
                    fr.srcIp = scanner.nextLine();
                }

                System.out.println("Please provide a name for source IP:");
                fr.srcName = scanner.nextLine();

                System.out.println("Please provide destination IP:");
                fr.dstIp = scanner.nextLine();

                System.out.println("Please provide a name for destination IP:");
                fr.dstName = scanner.nextLine();

                if (!svcPort.isEmpty()) {
                    fr.svcPort = svcPort;
                } else {
                    System.out.println("Please provide port:");
                    fr.svcPort = scanner.nextLine();
                }

                System.out.println("Please provide a name for port:");
                fr.svcName = scanner.nextLine();

                System.out.println("Please provide the protocol:");
                fr.protocol = scanner.nextLine();

                System.out.println("Creating a firewall request for " + fr.toString());
            }
             */
        }
    }
}

