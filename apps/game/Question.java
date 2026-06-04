package game;

import java.util.List;

public record Question(String text, List<String> options, int correctIndex) {

  public static final List<Question> ALL =
      List.of(
          new Question("国会があるのは東京ですが、造幣局があるのは？", List.of("札幌", "名古屋", "大阪", "福岡"), 2),
          new Question("2026WBC優勝国は？", List.of("日本", "ベネズエラ", "アメリカ", "メキシコ"), 1),
          new Question("水の化学式は？", List.of("CO2", "NaCl", "H2O", "O2"), 2),
          new Question("世界で一番高い山は？", List.of("K2", "マッキンリー", "エベレスト", "モンブラン"), 2),
          new Question("円周率を小数点以下2桁まで表すと？", List.of("3.14", "3.15", "3.12", "3.16"), 0),
          new Question("日本の首都は？", List.of("大阪", "京都", "東京", "名古屋"), 2),
          new Question("日本で一番高い山は？", List.of("富士山", "北岳", "槍ヶ岳", "穂高岳"), 0),
          new Question("日本の通貨単位は？", List.of("ドル", "円", "ユーロ", "ウォン"), 1),
          new Question("太陽系で地球の1つ外側を回る惑星は？", List.of("金星", "火星", "木星", "水星"), 1),
          new Question("酸素の元素記号は？", List.of("O", "H", "C", "N"), 0),
          new Question("金の元素記号は？", List.of("Ag", "Au", "Fe", "Cu"), 1),
          new Question("食塩の主成分である塩化ナトリウムの化学式は？", List.of("NaCl", "H2O", "CO2", "NH3"), 0),
          new Question("二酸化炭素の化学式は？", List.of("CO", "CO2", "O2", "C2H6"), 1),
          new Question("人間の血液を全身に送り出す器官は？", List.of("肺", "胃", "心臓", "腎臓"), 2),
          new Question("植物が光を使って養分を作る働きを何という？", List.of("呼吸", "光合成", "消化", "蒸発"), 1),
          new Question("日本の国会は何院と何院から成る？", List.of("衆議院と参議院", "上院と下院", "府議会と市議会", "最高裁と内閣"), 0),
          new Question("日本国憲法が施行された年は？", List.of("1945年", "1946年", "1947年", "1950年"), 2),
          new Question("内閣の長である人物を何という？", List.of("天皇", "最高裁判所長官", "内閣総理大臣", "衆議院議長"), 2),
          new Question("日本の三権分立に含まれないものは？", List.of("立法", "行政", "司法", "報道"), 3),
          new Question("日本銀行が主に行う役割は？", List.of("法律を作る", "金融政策を行う", "裁判を行う", "道路を建設する"), 1),
          new Question("国際連合の本部がある都市は？", List.of("ロンドン", "ニューヨーク", "パリ", "ジュネーブ"), 1),
          new Question("アメリカ合衆国の首都は？", List.of("ニューヨーク", "ロサンゼルス", "ワシントンD.C.", "シカゴ"), 2),
          new Question("フランスの首都は？", List.of("パリ", "ローマ", "ベルリン", "マドリード"), 0),
          new Question("イギリスの首都は？", List.of("マンチェスター", "ロンドン", "リバプール", "エディンバラ"), 1),
          new Question("中国の首都は？", List.of("上海", "北京", "広州", "深圳"), 1),
          new Question("世界で最も面積が大きい国は？", List.of("中国", "カナダ", "アメリカ", "ロシア"), 3),
          new Question("赤道が通る地域として正しいものは？", List.of("北極", "南極", "地球の中央付近", "日本列島"), 2),
          new Question("日本で最も面積が大きい都道府県は？", List.of("北海道", "岩手県", "長野県", "東京都"), 0),
          new Question("日本で最も人口が多い都道府県は？", List.of("大阪府", "神奈川県", "東京都", "愛知県"), 2),
          new Question("琵琶湖がある都道府県は？", List.of("滋賀県", "京都府", "奈良県", "三重県"), 0),
          new Question("阿蘇山がある都道府県は？", List.of("熊本県", "鹿児島県", "長崎県", "宮崎県"), 0),
          new Question("厳島神社がある都道府県は？", List.of("広島県", "島根県", "山口県", "岡山県"), 0),
          new Question("金閣寺がある都市は？", List.of("奈良市", "京都市", "大阪市", "神戸市"), 1),
          new Question("東大寺の大仏がある都市は？", List.of("京都市", "奈良市", "鎌倉市", "大阪市"), 1),
          new Question("姫路城がある都道府県は？", List.of("兵庫県", "大阪府", "岡山県", "香川県"), 0),
          new Question("織田信長が本能寺の変で討たれた年は？", List.of("1560年", "1582年", "1600年", "1603年"), 1),
          new Question("江戸幕府を開いた人物は？", List.of("豊臣秀吉", "徳川家康", "織田信長", "徳川吉宗"), 1),
          new Question("関ヶ原の戦いが起こった年は？", List.of("1590年", "1600年", "1603年", "1615年"), 1),
          new Question("明治維新が始まった時期として最も近い年は？", List.of("1603年", "1868年", "1945年", "1989年"), 1),
          new Question("鎌倉幕府を開いた人物は？", List.of("源頼朝", "足利尊氏", "平清盛", "北条時宗"), 0),
          new Question("室町幕府を開いた人物は？", List.of("足利尊氏", "徳川家康", "源頼朝", "豊臣秀吉"), 0),
          new Question("大化の改新が始まった年は？", List.of("538年", "645年", "710年", "794年"), 1),
          new Question("平安京に都が移された年は？", List.of("710年", "794年", "1185年", "1333年"), 1),
          new Question("第二次世界大戦が終わった年は？", List.of("1939年", "1941年", "1945年", "1951年"), 2),
          new Question("日本が国際連合に加盟した年は？", List.of("1945年", "1951年", "1956年", "1964年"), 2),
          new Question("1バイトは通常何ビット？", List.of("4ビット", "8ビット", "16ビット", "32ビット"), 1),
          new Question("コンピュータで情報を0と1で表す方式を何という？", List.of("十進法", "二進法", "六十進法", "ローマ数字"), 1),
          new Question("Webページを記述するためによく使われる言語は？", List.of("HTML", "SQL", "TCP", "PNG"), 0),
          new Question(
              "プログラムの誤りを見つけて修正する作業を何という？", List.of("コンパイル", "デバッグ", "ダウンロード", "インストール"), 1),
          new Question(
              "Javaでクラスを定義するときに使うキーワードは？", List.of("class", "def", "function", "module"), 0),
          new Question("Javaで整数型を表す代表的な型は？", List.of("String", "int", "boolean", "double"), 1),
          new Question("Javaで文字列を表す代表的な型は？", List.of("int", "String", "char[]のみ", "boolean"), 1),
          new Question("Javaで真偽値を表す型は？", List.of("boolean", "double", "String", "float"), 0),
          new Question(
              "Javaで標準出力に文字列を表示する命令は？",
              List.of("System.out.println", "print.out.System", "Console.write", "String.println"),
              0),
          new Question(
              "ネットワーク通信で、相手のアプリケーションを識別するために使う番号は？",
              List.of("IPアドレス", "ポート番号", "MACアドレス", "ファイル番号"),
              1));
}
