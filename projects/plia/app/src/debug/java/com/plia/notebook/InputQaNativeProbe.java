package com.plia.notebook;

/** Replace this debug-only probe only after a production native-ink API actually exists. */
final class InputQaNativeProbe {
    private InputQaNativeProbe() {}
    static String snapshot(InkView ink) {
        return "status=NOT_INTEGRATED enable_count=UNAVAILABLE disable_count=UNAVAILABLE native_sample_count=UNAVAILABLE";
    }
}
