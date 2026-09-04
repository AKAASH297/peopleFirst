package com.peoplefirst.agent.intent;

import java.util.List;
import java.util.Optional;

/**
 * Lightweight fuzzy string matching utility using Levenshtein (edit) distance.
 * Enables typo-tolerant intent classification and entity extraction without
 * any external NLP dependencies — pure Java implementation.
 */
public class FuzzyMatcher {

    private FuzzyMatcher() {}

    /**
     * Computes the Levenshtein (edit) distance between two strings.
     * Edit distance = minimum number of single-character insertions, deletions,
     * or substitutions required to transform one string into another.
     */
    public static int editDistance(String a, String b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        int lenA = a.length();
        int lenB = b.length();
        if (lenA == 0) return lenB;
        if (lenB == 0) return lenA;

        // Optimised single-row DP
        int[] prev = new int[lenB + 1];
        for (int j = 0; j <= lenB; j++) prev[j] = j;

        for (int i = 1; i <= lenA; i++) {
            int[] curr = new int[lenB + 1];
            curr[0] = i;
            for (int j = 1; j <= lenB; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            prev = curr;
        }
        return prev[lenB];
    }

    /**
     * Returns true if the edit distance between input and target is <= maxDistance.
     */
    public static boolean fuzzyMatch(String input, String target, int maxDistance) {
        if (input == null || target == null) return false;
        return editDistance(input.toLowerCase(), target.toLowerCase()) <= maxDistance;
    }

    /**
     * Finds the best matching candidate from a list, within maxDistance.
     * Returns Optional.empty() if no candidate is close enough.
     */
    public static Optional<String> findBestMatch(String input, List<String> candidates, int maxDistance) {
        if (input == null || candidates == null || candidates.isEmpty()) return Optional.empty();
        String inputLower = input.toLowerCase();
        String bestCandidate = null;
        int bestDist = maxDistance + 1;

        for (String candidate : candidates) {
            int dist = editDistance(inputLower, candidate.toLowerCase());
            if (dist < bestDist) {
                bestDist = dist;
                bestCandidate = candidate;
            }
        }
        return (bestCandidate != null && bestDist <= maxDistance) ? Optional.of(bestCandidate) : Optional.empty();
    }

    /**
     * Fuzzy version of String.contains(): checks if ANY word (token) in the text
     * fuzzy-matches the keyword within maxDistance.
     */
    public static boolean fuzzyContains(String text, String keyword, int maxDistance) {
        if (text == null || keyword == null) return false;
        String[] tokens = text.toLowerCase().split("\\s+");
        String keyLower = keyword.toLowerCase();
        for (String token : tokens) {
            // Strip common punctuation from token edges
            String clean = token.replaceAll("^[^a-zA-Z0-9]+|[^a-zA-Z0-9]+$", "");
            if (!clean.isEmpty() && editDistance(clean, keyLower) <= maxDistance) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if any word in text fuzzy-matches ANY keyword in the list.
     * Returns the first matched keyword, or null if none match.
     */
    public static String fuzzyContainsAny(String text, List<String> keywords, int maxDistance) {
        if (text == null || keywords == null) return null;
        for (String kw : keywords) {
            if (fuzzyContains(text, kw, maxDistance)) {
                return kw;
            }
        }
        return null;
    }

    /**
     * Checks if a word in text is a plausible fuzzy match for a multi-word phrase.
     * Splits the phrase and checks if consecutive tokens in text match phrase words.
     */
    public static boolean fuzzyContainsPhrase(String text, String phrase, int maxDistPerWord) {
        if (text == null || phrase == null) return false;
        String[] textTokens = text.toLowerCase().split("\\s+");
        String[] phraseTokens = phrase.toLowerCase().split("\\s+");
        if (phraseTokens.length == 0 || textTokens.length < phraseTokens.length) return false;

        for (int i = 0; i <= textTokens.length - phraseTokens.length; i++) {
            boolean allMatch = true;
            for (int j = 0; j < phraseTokens.length; j++) {
                String clean = textTokens[i + j].replaceAll("^[^a-zA-Z0-9]+|[^a-zA-Z0-9]+$", "");
                if (editDistance(clean, phraseTokens[j]) > maxDistPerWord) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) return true;
        }
        return false;
    }
}
