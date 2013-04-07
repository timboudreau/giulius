package com.mastfrog.giulius.tests.ns;

import com.google.inject.Inject;
import com.mastfrog.guicy.annotations.Defaults;
import com.mastfrog.settings.Settings;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static com.mastfrog.giulius.tests.ns.WildcardOptions.*;
import com.mastfrog.guicy.annotations.Namespace;

@Defaults(value = {DISABLED + '=' + DISABLED_DEFAULT, WILDCARD_MIN_PREFIX + '='
    + WILDCARD_MIN_PREFIX_DEFAULT,
    NUMERIC_WILDCARD_EXCEPTED + '=' + NUMERIC_WILDCARD_EXCEPTED_DEFAULT,
    MAX_WILDCARD_PER_TERM + '=' + MAX_WILDCARD_PER_TERM_DEFAULT,
    MAX_WILDCARD_TERMS + '=' + MAX_WILDCARD_TERMS_DEFAULT,
    WILDCARD_LIST + '=' + DEFAULT_WILDCARDS}, namespace =
@Namespace("wildcard"))
@Namespace("wildcard")
class WildcardOptionsImpl implements WildcardOptions {

    private final char[] wildcardList;
    private final Settings defaults;

    @Inject
    public WildcardOptionsImpl(Settings settings) {
        this.defaults = settings;
        //Double check that we have values for everything, but only if 
        //assertions are turned on
        assert sanityCheck() : "Missing default values";
        wildcardList = settings.getString(WILDCARD_LIST).toCharArray();
        //Sort so we can use Arrays.binarySearch()
        Arrays.sort(wildcardList);
    }

    private boolean sanityCheck() {
        Set<String> keys = new HashSet<String>(defaults.allKeys());
        String[] all = new String[]{MAX_WILDCARD_TERMS, MAX_WILDCARD_PER_TERM, DISABLED, WILDCARD_MIN_PREFIX, NUMERIC_WILDCARD_EXCEPTED};
        if (!keys.containsAll(Arrays.asList(all))) {
            Set<String> missing = new HashSet<String>(Arrays.asList(all));
            missing.removeAll(keys);
            throw new AssertionError("Settings missing keys: " + missing);
        }
        return true;
    }

    @Override
    public char[] getWildcardList() {
        char[] copy = new char[wildcardList.length];
        System.arraycopy(wildcardList, 0, copy, 0, wildcardList.length);
        return copy;
    }

    @Override
    public boolean isDisabled() {
        return defaults.getBoolean(DISABLED);
    }

    @Override
    public int getWildcardMinPrefix() {
        return defaults.getInt(WILDCARD_MIN_PREFIX);
    }

    @Override
    public boolean isNumericWildcardExcepted() {
        return defaults.getBoolean(NUMERIC_WILDCARD_EXCEPTED);
    }

    public int getMaxWildcardPerTerm() {
        return defaults.getInt(MAX_WILDCARD_PER_TERM);
    }

    @Override
    public int getMaxWildcardTerms() {
        return defaults.getInt(MAX_WILDCARD_TERMS);
    }

    @Override
    public String removeLowLevelQueryOperators(String query) {
        if (query == null) {
            return query;
        }

        return query.replaceAll("#\\w+\\(", "(");
    }

    @Override
    public boolean containsWildcard(String word) {
        if (word == null) {
            return false;
        }

        // First, we need to sanitize the input to remove all low level query syntax
        word = removeLowLevelQueryOperators(word);

        char[] chars = word.toCharArray();
        for (char wildcard : chars) {
            if (Arrays.binarySearch(wildcardList, wildcard) >= 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int countTermsWithWildcardCharacters(String query) {
        int termCount = 0;

        if (query != null) {
            for (String term : query.split("\\s+")) {
                if (containsWildcard(term)) {
                    termCount++;
                }
            }
        }

        return termCount;
    }

    @Override
    public int countWildcardCharacters(String word) {
        int wildcardCount = 0;

        if (word == null) {
            return wildcardCount;
        }

        word = removeLowLevelQueryOperators(word);

        for (int i = 0; i < word.length(); i++) {
            for (char wildcard : wildcardList) {
                if (wildcard == word.charAt(i)) {
                    wildcardCount++;
                }
            }
        }

        return wildcardCount;
    }

    @Override
    public CharacterType getCharacterType(char character) {
        boolean isWildcard = Arrays.binarySearch(wildcardList, character) >= 0;
        return !isWildcard ? CharacterType.NOT_A_WILDCARD : character == '#' ? CharacterType.NUMERIC_WILDCARD : CharacterType.WILDCARD;
    }
}
