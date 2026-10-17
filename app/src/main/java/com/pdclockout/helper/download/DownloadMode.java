package com.pdclockout.helper.download;

public enum DownloadMode {
    P2160("최대 2160p MP4 (4K)", 2160, false, false),
    P1440("최대 1440p MP4 (QHD)", 1440, false, false),
    P1080("최대 1080p MP4 (FHD)", 1080, true, false),
    P720("최대 720p MP4 (HD)", 720, true, false),
    P480("최대 480p MP4", 480, true, false),
    MP3("오디오만 MP3 (최고음질)", 0, false, true);

    public final String label;
    public final int height;
    public final boolean preferH264Aac;
    public final boolean audioOnly;

    DownloadMode(String label, int height, boolean preferH264Aac, boolean audioOnly) {
        this.label = label;
        this.height = height;
        this.preferH264Aac = preferH264Aac;
        this.audioOnly = audioOnly;
    }

    public static DownloadMode fromOrdinalSafe(int n) {
        DownloadMode[] values = values();
        return n >= 0 && n < values.length ? values[n] : P1080;
    }

    public String formatSelector() {
        if (audioOnly) return "ba/b";
        if (preferH264Aac) {
            return String.format(
                    "bv[height<=%d][vcodec^=avc1][ext=mp4]+ba[acodec^=mp4a][ext=m4a]/" +
                            "b[height<=%d][vcodec^=avc1][acodec^=mp4a][ext=mp4]/" +
                            "bv[height<=%d][vcodec^=avc1]+ba[acodec^=mp4a]/" +
                            "b[height<=%d][ext=mp4]/bv*[height<=%d]+ba/b[height<=%d]",
                    height, height, height, height, height, height
            );
        }
        return String.format("bv*[height<=%d]+ba/b[height<=%d]", height, height);
    }
}
