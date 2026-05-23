package apps.game;

import java.util.List;

public record Question(String text, List<String> options, int correctIndex) {

    public static final List<Question> ALL = List.of(
        new Question("国会があるのは東京ですが、造幣局があるのは？",
            List.of("札幌", "名古屋", "大阪", "福岡"), 2),
        new Question("2026WBC優勝国は？",
            List.of("日本", "ベネズエラ", "アメリカ", "メキシコ"), 1),
        new Question("水の化学式は？",
            List.of("CO2", "NaCl", "H2O", "O2"), 2),
        new Question("世界で一番高い山は？",
            List.of("K2", "マッキンリー", "エベレスト", "モンブラン"), 2),
        new Question("円周率を小数点以下2桁まで表すと？",
            List.of("3.14", "3.15", "3.12", "3.16"), 0)
    );
}
