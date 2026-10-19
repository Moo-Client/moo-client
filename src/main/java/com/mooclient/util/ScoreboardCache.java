package com.mooclient.util;

import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

import java.util.List;

public class ScoreboardCache {
    public static class CachedScoreboardRow {
        public final Text nameText;
        public final Text scoreText;
        public final int scoreWidth;

        public CachedScoreboardRow(Text nameText, Text scoreText, int scoreWidth) {
            this.nameText = nameText;
            this.scoreText = scoreText;
            this.scoreWidth = scoreWidth;
        }
    }

    public static class CachedScoreboardData {
        public ScoreboardObjective objective;
        public int rawSize;
        public long lastUpdateTick = -1;
        public Text titleText;
        public int titleWidth;
        public int totalWidth;
        public int totalHeight;
        public List<CachedScoreboardRow> rows;
    }

    public static final CachedScoreboardData SCOREBOARD_CACHE = new CachedScoreboardData();
}
