package com.example.addon.features;

/**
 * exchams – nơi chứa KEY A
 * Chỉ là mảnh đầu của link gốc.
 */
public final class exchams {

    private exchams() {}

    /* ==================================================
       KEY A – LINK FRAGMENT (PHẦN ĐẦU)
       ================================================== */

    // "https://raw.githubusercontent.com/"
    private static final char[] A = {
        'h','t','t','p','s',':','/','/',
        'r','a','w','.',
        'g','i','t','h','u','b','u','s','e','r','c','o','n','t','e','n','t','.',
        'c','o','m','/'
    };

    // expose qua method (KHÔNG public field)
    public static char a(int i) {
        return A[i];
    }

    public static int len() {
        return A.length;
    }
}
