package com.pdclockout.helper.download;

import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.util.List;

public final class RequestFactory {
    private RequestFactory() {}

    public static YoutubeDLRequest build(String url, File workDir, DownloadMode mode, List<String> extra) {
        YoutubeDLRequest r = new YoutubeDLRequest(url);
        r.addOption("--ignore-config");
        r.addOption("--newline");
        r.addOption("--no-colors");
        r.addOption("--encoding", "utf-8");
        r.addOption("--retries", "4");
        r.addOption("--fragment-retries", "4");
        r.addOption("--extractor-retries", "3");
        r.addOption("--no-overwrites");

        int instaIndex = UrlTools.instagramRequestedIndex(url);
        if (instaIndex > 0) {
            r.addOption("--yes-playlist");
            r.addOption("--playlist-items", String.valueOf(instaIndex));
        } else {
            r.addOption("--no-playlist");
        }

        if (mode.audioOnly) {
            r.addOption("-f", "ba/b");
            r.addOption("--extract-audio");
            r.addOption("--audio-format", "mp3");
            r.addOption("--audio-quality", "0");
        } else {
            r.addOption("-f", mode.formatSelector());
            r.addOption("--merge-output-format", "mp4");
            r.addOption("--remux-video", "mp4");
            if (mode == DownloadMode.P2160 || mode == DownloadMode.P1440) {
                r.addOption("--recode-video", "mp4");
                r.addOption("--postprocessor-args",
                        "VideoConvertor+ffmpeg:-c:v libx264 -preset veryfast -crf 19 -pix_fmt yuv420p -c:a aac -b:a 192k -ar 48000 -movflags +faststart");
            }
        }

        if (UrlTools.isYouTube(url)) {
            // youtubedl-android 0.18.1 bundles QuickJS for current YouTube JS challenges.
            r.addOption("--js-runtimes", "quickjs");
            r.addOption("--remote-components", "ejs:github");
        }

        r.addOption("--print", "after_move:PDCLOCKOUT_META:%(.{filepath,_filename,title,extractor_key,uploader,uploader_id,channel,webpage_url})+j");
        r.addOption("-o", "MEDIA_%(extractor_key)s_%(id)s.%(ext)s");
        r.addOption("-P", workDir.getAbsolutePath());

        if (extra != null) {
            for (int i = 0; i < extra.size(); i += 2) {
                String key = extra.get(i);
                if (i + 1 < extra.size() && extra.get(i + 1) != null) r.addOption(key, extra.get(i + 1));
                else r.addOption(key);
            }
        }
        return r;
    }
}
