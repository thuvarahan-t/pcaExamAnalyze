package com.example.pcaExamAnalyze.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates one-way spelling restorations from Tamil-script loanwords. */
final class FeedbackEnglishSpellings {
    private static final String WORD_CHARS = "[\\p{L}\\p{M}\\p{N}\\u200c\\u200d]";
    private static final Pattern WORDS = Pattern.compile(WORD_CHARS + "+(?:-" + WORD_CHARS + "+)*|\\p{S}");
    private static final Pattern ENGLISH_WITH_SUFFIX = Pattern.compile(
        "([a-zA-Z]+)(?:-(க்கு|கு|க்கும்|க்காக|ல|ல்|லே|லும்|லிருந்து|இல்|இலும்|ஐ|உம்|ஓட|உடன்))?");
    private static final Set<String> CLASS_OR_GLASS = Set.of(
        "கிளாஸ்", "கிளாஸு", "க்ளாஸ்", "க்ளாஸு", "கிளாசு", "கிளாசி");
    private static final Map<String, Set<String>> ALIASES = Map.of(
        "tuition", Set.of("ட்யூஷன்", "ட்யூஷனு", "டியூஷன்", "டியூஷனு", "டியூசன்", "டியூசனு", "ட்டியுசன்", "ட்டியுசனு"),
        "notes", Set.of("நோட்ஸ்", "நோட்ஸு", "நோட்ஸ்‌"),
        "physics", Set.of("ஃபிசிக்ஸ்", "பிசிக்ஸ்", "பிஸிக்ஸ்", "பிசிக்ஸு", "பிஸிக்ஸு"),
        "mcq", Set.of("எம்சிக்யூ", "எம்சிகியூ"),
        "paper", Set.of("பேப்பர்", "பேப்பரு"),
        "exam", Set.of("எக்ஸாம்", "எக்ஸாமு"),
        "lesson", Set.of("லெசன்", "லெஸன்", "லெசனு", "லெஸனு"),
        "improve", Set.of("இம்ப்ரூவ்", "இம்புரூவ்"),
        "improvement", Set.of("இம்ப்ரூவ்மெண்ட்", "இம்புரூவ்மெண்ட்", "இம்ப்ரூவ்மெண்டு", "இம்புரூவ்மெண்டு"));
    private static final Pattern CLASS_CONTEXT = Pattern.compile(
        "(?iu)(?<![a-z])(?:tuition|lesson|notes|physics|MCQ|exam)(?![a-z])"
            + "|டியூஷன|ட்யூஷன|நோட்ஸ்|பாடம்|பாடத்|படிப்|படிச்|வகுப்பு|ஆசிரிய|விளங்கு|பள்ளி|கல்லூரி");
    private static final Pattern CLASS_ATTENDANCE = Pattern.compile(
        "^[ ,]*(?:வாறன்|வாரன்|வாறேன்|வாரேன்|வரேன்|வரணும்|வருவேன்|போறன்|போறேன்|போனன்|போனேன்|போவேன்)"
            + "(?!" + WORD_CHARS + ")");
    private static final Pattern GLASS_CONTEXT = Pattern.compile("தண்ணி|தண்ணீர்|ஊத்து|ஊற்று|உடைஞ்|உடைந்த|கண்ணாடி|குடிச்ச");

    private FeedbackEnglishSpellings() {}

    /** Applies only validated spelling changes to the source used for the final preservation check. */
    static String comparableSource(String original, String edited) {
        var before = WORDS.matcher(original);
        var after = WORDS.matcher(edited);
        var comparable = new StringBuilder(original.length());
        while (before.find()) {
            if (!after.find()) return null;
            if (before.group().equals(after.group())) continue;
            if (!isAllowed(original, before, after.group())) return null;
            before.appendReplacement(comparable, Matcher.quoteReplacement(after.group()));
        }
        return after.find() ? null : before.appendTail(comparable).toString();
    }

    private static boolean isAllowed(String original, Matcher before, String replacement) {
        var restored = ENGLISH_WITH_SUFFIX.matcher(replacement);
        if (!restored.matches()) return false;
        String word = restored.group(1).toLowerCase(Locale.ROOT);
        String stem = before.group();
        String suffix = restored.group(2);
        if (suffix != null) {
            if (!stem.endsWith(suffix)) return false;
            stem = stem.substring(0, stem.length() - suffix.length());
            if (stem.endsWith("-")) stem = stem.substring(0, stem.length() - 1);
        }

        if (!word.equals("class") && !word.equals("glass")) {
            return ALIASES.getOrDefault(word, Set.of()).contains(stem);
        }
        if (!(CLASS_OR_GLASS.contains(stem) || word.equals("class") && stem.equals("கிழாசி"))) return false;
        String context = sentenceAt(original, before.start(), before.end());
        boolean hasGlassContext = GLASS_CONTEXT.matcher(context).find();
        if (word.equals("glass")) return hasGlassContext;
        return !hasGlassContext && (CLASS_CONTEXT.matcher(context).find()
            || "க்கு".equals(suffix) && CLASS_ATTENDANCE.matcher(original.substring(before.end())).find());
    }

    private static String sentenceAt(String source, int start, int end) {
        while (start > 0 && ".!?".indexOf(source.charAt(start - 1)) < 0) start--;
        while (end < source.length() && ".!?".indexOf(source.charAt(end)) < 0) end++;
        return source.substring(start, end);
    }
}
