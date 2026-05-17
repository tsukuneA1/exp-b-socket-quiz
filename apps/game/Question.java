package apps.game;

import java.util.List;

public record Question(String text, List<String> options, int correctIndex) {

    public static final List<Question> ALL = List.of(
        new Question("日本の首都は？",
            List.of("大阪", "東京", "名古屋", "福岡"), 1),
        new Question("1 + 1 は？",
            List.of("1", "2", "3", "4"), 1),
        new Question("水の化学式は？",
            List.of("CO2", "NaCl", "H2O", "O2"), 2),
        new Question("世界で一番高い山は？",
            List.of("K2", "マッキンリー", "エベレスト", "モンブラン"), 2),
        new Question("円周率を小数点以下2桁まで表すと？",
            List.of("3.14", "3.15", "3.12", "3.16"), 0)
    );
}
