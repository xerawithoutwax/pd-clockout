package com.pdclockout.helper.download;

import org.json.JSONObject;

import java.io.File;
import java.util.Locale;

public final class NameTools {
    private NameTools() {}

    public static String platformLabel(String extractorKey, String webpageUrl) {
        String all = ((extractorKey == null ? "" : extractorKey) + " " + (webpageUrl == null ? "" : webpageUrl)).toLowerCase(Locale.ROOT);
        if (all.contains("youtube") || all.contains("youtu.be")) return "유튜브";
        if (all.contains("instagram")) return "인스타";
        if (all.contains("tiktok")) return "틱톡";
        if (all.contains("facebook") || all.contains("fb.watch")) return "페이스북";
        if (all.contains("twitter") || all.contains("x.com")) return "X";
        if (all.contains("vimeo")) return "비메오";
        if (all.contains("twitch")) return "트위치";
        if (all.contains("naver")) return "네이버";
        if (all.contains("kakao")) return "카카오TV";
        if (all.contains("dailymotion")) return "데일리모션";
        if (all.contains("reddit")) return "레딧";
        if (all.contains("soundcloud")) return "사운드클라우드";
        return "ETC";
    }

    public static String sanitize(String in, String fallback, int maxCodePoints) {
        String s = in == null ? "" : in.trim();
        StringBuilder b = new StringBuilder();
        String invalid = "<>:\"/\\|?*";
        for (int i = 0; i < s.length();) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp < 32 || invalid.indexOf(cp) >= 0) continue;
            b.appendCodePoint(cp);
        }
        s = b.toString().trim().replaceAll(" {2,}", " ");
        s = s.replaceAll("[. ]+$", "");
        if (s.isEmpty()) s = fallback;
        if (maxCodePoints > 0 && s.codePointCount(0, s.length()) > maxCodePoints) {
            int end = s.offsetByCodePoints(0, maxCodePoints);
            s = s.substring(0, end).trim().replaceAll("[. ]+$", "");
        }
        return s;
    }

    public static String smartTrimTitle(String in, int maxCodePoints) {
        String s = in == null ? "" : in.trim();
        int count = s.codePointCount(0, s.length());
        if (maxCodePoints <= 0 || count <= maxCodePoints) return s;
        int[] cps = s.codePoints().toArray();
        int cut = maxCodePoints;
        int minNatural = maxCodePoints * 3 / 4;
        String boundaries = " \t\n·|/-–—:;,.!?)]}";
        for (int i = maxCodePoints - 1; i >= minNatural; i--) {
            if (boundaries.indexOf(cps[i]) >= 0) {
                cut = i;
                break;
            }
        }
        if (cut < 1) cut = maxCodePoints;
        String out = new String(cps, 0, cut).trim().replaceAll("[ ._\\-–—|·,:;!?\\)\\]\\}]+$", "");
        if (out.isEmpty()) out = new String(cps, 0, maxCodePoints).trim();
        return out;
    }

    public static String buildFinalName(JSONObject meta, File file, String prefix) {
        String title = meta == null ? "" : meta.optString("title", "");
        title = sanitize(title, "제목없음", 0);
        title = smartTrimTitle(title, 20);

        String account = "알수없음";
        if (meta != null) {
            for (String key : new String[]{"channel", "uploader", "uploader_id"}) {
                String v = meta.optString(key, "").trim();
                if (!v.isEmpty() && !v.equalsIgnoreCase("NA") && !v.equalsIgnoreCase("null")) {
                    account = v;
                    break;
                }
            }
        }
        account = sanitize(account, "알수없음", 45);
        String extractor = meta == null ? "" : meta.optString("extractor_key", "");
        String webpage = meta == null ? "" : meta.optString("webpage_url", "");
        String platform = sanitize(platformLabel(extractor, webpage), "ETC", 30);

        String ext = extension(file.getName());
        if (ext.isEmpty()) ext = ".mp4";
        return (prefix == null ? "" : prefix) + title + "_출처" + platform + "_" + account + ext;
    }

    private static String extension(String name) {
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i) : "";
    }
}
