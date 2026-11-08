package cn.xfangfang.wiliwili.secondscreen;

/**
 * Colors of the second-screen UI, mirroring wiliwili's borealis theme.
 * Updated from native on every session open (light/dark + custom accent color).
 */
public final class SecondScreenTheme {
    public static boolean dark = false;
    public static int background = 0xFFEBEBEB;
    public static int surface = 0xFFF5F6F7;
    public static int text = 0xFF2D2D2D;
    public static int textSecondary = 0xFF9499A0;
    public static int accent = 0xFFFF6699;
    public static int divider = 0xFFD0D0D0;
    public static int name = 0xFF6693B6;
    public static int buttonBackground = 0xFFE9E9E9;
    public static int onAccent = 0xFFFFFFFF;
    public static String keymap = "xbox";
    public static boolean swapABXY = false;

    private SecondScreenTheme() {
    }

    public static void apply(boolean isDark, int background, int surface, int text,
                             int textSecondary, int accent, int divider, int name) {
        SecondScreenTheme.dark = isDark;
        SecondScreenTheme.background = background;
        SecondScreenTheme.surface = surface;
        SecondScreenTheme.text = text;
        SecondScreenTheme.textSecondary = textSecondary;
        SecondScreenTheme.accent = accent;
        SecondScreenTheme.divider = divider;
        SecondScreenTheme.name = name;
        SecondScreenTheme.buttonBackground = isDark ? 0xFF3A3A3A : 0xFFE9E9E9;
        SecondScreenTheme.onAccent = 0xFFFFFFFF;
    }
}
