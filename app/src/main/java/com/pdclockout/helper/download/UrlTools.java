package com.pdclockout.helper.download;

import android.net.Uri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UrlTools {
    private static final Pattern URL_RE = Pattern.compile("https?://[^\\s<>\\\"']+");

    private UrlTools() {}

    public static List<String> extractAllUrls(String raw) {
        List<String> out = new ArrayList<>();
        Matcher m = URL_RE.matcher(raw == null ? "" : raw);
        while (m.find()) {
            String u = trimTail(m.group());
            if (!u.isEmpty()) out.add(u);
        }
        if (out.isEmpty() && raw != null) {
            String one = raw.trim();
            if (one.toLowerCase(Locale.ROOT).startsWith("http://") || one.toLowerCase(Locale.ROOT).startsWith("https://")) {
                out.add(one);
            }
        }
        return out;
    }

    public static List<DownloadItem> buildItems(String raw) {
        List<String> urls = extractAllUrls(raw);
        List<DownloadItem> items = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (int n = 0; n < urls.size(); n++) {
            String original = urls.get(n);
            DownloadItem item = new DownloadItem();
            item.index = n + 1;
            item.rawUrl = original;
            try {
                Normalized normalized = normalize(original);
                item.mediaUrl = normalized.url;
                item.note = normalized.note;
                item.supported = isSupported(item.mediaUrl);
            } catch (Exception e) {
                item.mediaUrl = original;
                item.error = e.getMessage() == null ? "URL 해석 실패" : e.getMessage();
                item.supported = false;
            }
            String key = identityKey(item.mediaUrl);
            if (key.isEmpty()) key = item.rawUrl.trim();
            Integer first = seen.get(key);
            if (first != null) {
                item.duplicate = true;
                item.duplicateOfIndex = first;
            } else if (!key.isEmpty()) {
                seen.put(key, item.index);
            }
            items.add(item);
        }
        return items;
    }

    public static boolean isYouTube(String raw) {
        String h = host(raw);
        return h.equals("youtube.com") || h.endsWith(".youtube.com") || h.equals("youtu.be") || h.equals("music.youtube.com");
    }

    public static boolean isInstagram(String raw) {
        String h = host(raw);
        return h.equals("instagram.com") || h.endsWith(".instagram.com");
    }

    public static boolean isFacebook(String raw) {
        String h = host(raw);
        return h.equals("facebook.com") || h.endsWith(".facebook.com") || h.equals("fb.watch");
    }

    public static boolean isTikTok(String raw) {
        String h = host(raw);
        return h.equals("tiktok.com") || h.endsWith(".tiktok.com") || h.equals("tiktokv.com") || h.equals("vm.tiktok.com") || h.equals("vt.tiktok.com");
    }

    public static boolean isSupported(String raw) {
        return isYouTube(raw) || isInstagram(raw) || isFacebook(raw) || isTikTok(raw);
    }

    public static String youtubeVideoId(String raw) {
        try {
            Uri u = Uri.parse(firstUrl(raw));
            String h = cleanHost(u.getHost());
            if ("youtu.be".equals(h)) {
                List<String> seg = u.getPathSegments();
                return seg.isEmpty() ? "" : seg.get(0);
            }
            if (!(h.equals("youtube.com") || h.equals("m.youtube.com") || h.equals("music.youtube.com"))) return "";
            String v = u.getQueryParameter("v");
            if (v != null && !v.trim().isEmpty()) return v.trim();
            List<String> seg = u.getPathSegments();
            if (seg.size() >= 2) {
                String kind = seg.get(0).toLowerCase(Locale.ROOT);
                if (kind.equals("shorts") || kind.equals("embed") || kind.equals("live")) return seg.get(1);
            }
        } catch (Exception ignored) {}
        return "";
    }

    public static int instagramRequestedIndex(String raw) {
        try {
            Uri u = Uri.parse(firstUrl(raw));
            if (!isInstagram(raw)) return 0;
            String v = u.getQueryParameter("img_index");
            if (v == null) return 0;
            int n = Integer.parseInt(v);
            return Math.max(0, n);
        } catch (Exception ignored) {
            return 0;
        }
    }

    public static String identityKey(String raw) {
        String id = youtubeVideoId(raw);
        return id.isEmpty() ? (raw == null ? "" : raw.trim()) : "youtube:" + id;
    }

    public static String firstUrl(String raw) {
        if (raw == null) return "";
        Matcher m = URL_RE.matcher(raw.trim());
        return m.find() ? trimTail(m.group()) : raw.trim();
    }

    private static String trimTail(String in) {
        String s = in == null ? "" : in.trim();
        while (!s.isEmpty() && ").,;!]}>".indexOf(s.charAt(s.length() - 1)) >= 0) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String host(String raw) {
        try {
            return cleanHost(Uri.parse(firstUrl(raw)).getHost());
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String cleanHost(String h) {
        if (h == null) return "";
        h = h.toLowerCase(Locale.ROOT);
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    public static Normalized normalize(String raw) throws Exception {
        String original = raw == null ? "" : raw.trim();
        String clean = firstUrl(original);
        Uri u = Uri.parse(clean);
        if (u.getScheme() == null || u.getHost() == null || !(u.getScheme().equals("http") || u.getScheme().equals("https"))) {
            throw new Exception("http/https 형식의 URL이 아닙니다");
        }

        String host = cleanHost(u.getHost());
        Uri.Builder b = u.buildUpon().fragment(null);
        boolean changed = !clean.equals(original);

        Map<String, List<String>> keep = new HashMap<>();
        Set<String> names = new LinkedHashSet<>(u.getQueryParameterNames());
        for (String name : names) {
            String low = name.toLowerCase(Locale.ROOT);
            if (low.startsWith("utm_")) {
                changed = true;
                continue;
            }
            List<String> values = u.getQueryParameters(name);
            keep.put(name, values);
        }

        if (host.equals("instagram.com")) {
            b.scheme("https").authority("www.instagram.com");
            List<String> seg = u.getPathSegments();
            if (seg.size() >= 2) {
                String kind = seg.get(0).toLowerCase(Locale.ROOT);
                String code = seg.get(1);
                if (kind.equals("reel") || kind.equals("reels")) {
                    b.path("/reel/" + code + "/");
                    changed = true;
                } else if (kind.equals("p") || kind.equals("tv")) {
                    b.path("/" + kind + "/" + code + "/");
                }
            }
            String imgIndex = u.getQueryParameter("img_index");
            keep.clear();
            if (imgIndex != null) {
                try {
                    int n = Integer.parseInt(imgIndex);
                    if (n > 0) keep.put("img_index", List.of(String.valueOf(n)));
                } catch (Exception ignored) {}
            }
            changed = true;
        } else if (host.equals("facebook.com") || host.equals("m.facebook.com")) {
            b.scheme("https").authority("www.facebook.com");
            remove(keep, "fbclid", "mibextid");
            List<String> seg = u.getPathSegments();
            if (seg.size() >= 2 && seg.get(0).equalsIgnoreCase("reel")) {
                b.path("/reel/" + seg.get(1) + "/");
                keep.clear();
            }
            changed = true;
        } else if (host.equals("tiktok.com")) {
            b.scheme("https").authority("www.tiktok.com");
            remove(keep, "is_from_webapp", "sender_device", "sender_web_id");
            List<String> seg = u.getPathSegments();
            if (seg.size() >= 3 && seg.get(0).startsWith("@") && seg.get(1).equalsIgnoreCase("video")) {
                b.path("/" + seg.get(0) + "/video/" + seg.get(2));
                keep.clear();
            }
            changed = true;
        } else if (host.equals("youtube.com") || host.equals("youtu.be")) {
            remove(keep, "feature", "si", "pp");
        }

        b.clearQuery();
        for (Map.Entry<String, List<String>> e : keep.entrySet()) {
            for (String v : e.getValue()) b.appendQueryParameter(e.getKey(), v);
        }
        String result = b.build().toString();
        if (!result.equals(clean)) changed = true;
        return new Normalized(result, changed ? "공유용 추적 파라미터/주소 형식을 다운로드용 정규 URL로 정리했습니다." : "");
    }

    private static void remove(Map<String, List<String>> map, String... keys) {
        for (String k : keys) map.remove(k);
    }

    public static final class Normalized {
        public final String url;
        public final String note;
        public Normalized(String url, String note) { this.url = url; this.note = note; }
    }
}
