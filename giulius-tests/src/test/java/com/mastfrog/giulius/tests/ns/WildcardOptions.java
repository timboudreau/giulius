package com.mastfrog.giulius.tests.ns;

import com.google.inject.ImplementedBy;
import com.mastfrog.guicy.annotations.Namespace;

@ImplementedBy(WildcardOptionsImpl.class)
@Namespace("wildcard")
public interface WildcardOptions {

    public static final String DISABLED = "Disabled";
    public static final boolean DISABLED_DEFAULT = false;
    public final static String WILDCARD_MIN_PREFIX = "WildcardMinPrefix";
    public final static int WILDCARD_MIN_PREFIX_DEFAULT = 2;
    public final static String NUMERIC_WILDCARD_EXCEPTED = "NumericWildcardExcepted";
    public final static String WILDCARD_LIST = "WildcardList";
    final static String DEFAULT_WILDCARDS = "*?#";
    public final static boolean NUMERIC_WILDCARD_EXCEPTED_DEFAULT = false;
    public final static String MAX_WILDCARD_PER_TERM = "MaxWildcardPerTerm";
    public final static int MAX_WILDCARD_PER_TERM_DEFAULT = 15;
    public final static String MAX_WILDCARD_TERMS = "MaxWildcardTerms";
    public final static int MAX_WILDCARD_TERMS_DEFAULT = 15;

    char[] getWildcardList();

    boolean isDisabled();

    int getWildcardMinPrefix();

    boolean isNumericWildcardExcepted();

    int getMaxWildcardPerTerm();

    int getMaxWildcardTerms();

    String removeLowLevelQueryOperators(String query);

    boolean containsWildcard(String word);

    int countTermsWithWildcardCharacters(String query);

    int countWildcardCharacters(String word);

    CharacterType getCharacterType(char character);

    public enum CharacterType {

        NUMERIC_WILDCARD,
        WILDCARD,
        NOT_A_WILDCARD;

        public boolean isWildcard() {
            return this != NOT_A_WILDCARD;
        }

        public boolean isNumeric() {
            return this == NUMERIC_WILDCARD;
        }
    }
}
