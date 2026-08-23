package jp.yuya.dev.developerdungeon.app;

import java.util.List;
import java.util.Map;

final class StageCatalog {
    private static final StageDefinition STAGE_ONE = new StageDefinition("STAGE-GIT-01", "第1現場 / リリース障害", "公開済み変更を取り消す",
            "公開済みの誤変更を、履歴を壊さずに戻す。", "公開済みのmainから必要な設定が消え、運用担当は次のリリース確認を進められない。新人のあなたは、先輩から緊急チケットを受け取った。共有履歴を壊さずに設定を戻そう。",
            "誤ったcommitがmainへ公開された。履歴を消さず、利用者へ安全な取り消しを届けること。", "誤commitを履歴に残したまま、正常な状態へ戻す。",
            "git status / git log --oneline / git show <12または40桁ID> / git revert --no-edit <12または40桁ID>",
            new StageOutcome("公開済みのmainに、必要な設定を削除する誤commitが含まれていました。",
                    "mainには誤commitが履歴として残り、その変更を打ち消す新しいcommitによって必要な設定が戻りました。",
                    "revertは公開済みの履歴を変えずに、誤変更だけを打ち消せます。",
                    "reset --hardで公開済みcommitを消すと、他メンバーが参照する履歴を書き換え、共有branchを混乱させるおそれがあります。",
                    "公開済み履歴を壊さずに復旧できたと判断するには、履歴と作業ツリーのどの状態を確認すべきでしょうか？",
                    "誤commitが履歴に残り、その変更を打ち消す新しいcommitによって作業ツリーが正常な状態へ戻っていることを確認します。",
                    "運用担当が設定の復旧確認を再開すると、先輩は「共有履歴を守る判断ができたね」と主人公にうなずいた。",
                    "主人公は、最短に見える操作ではなく、共有中の履歴を守る判断を初めて任された。"),
            StagePresentationPolicy.conceptOnlyBasic("状態確認", "履歴確認", "commit確認", "安全な取り消し"),
            learningCard("公開履歴の直近変更と、削除された設定の内容を確認する。",
                    "共有済みの履歴を残したまま、影響を打ち消す方法はどれ？",
                    List.of("打ち消しcommitを追加し、元commitを履歴に残す", "公開commitを履歴から外す"),
                    "元の変更を履歴に残したまま、必要な設定が戻り、作業ツリーがcleanになった。",
                    "運用担当へ、何を守って復旧したと報告する？",
                    List.of("共有履歴を残し、打ち消し結果を確認した", "履歴を消して見えなくした")));
    private static final StageDefinition STAGE_TWO = new StageDefinition("STAGE-GIT-02", "第2現場 / branchの取り違え", "間違ったbranchのcommitを移す",
            "未公開の通知機能commitを、正しいbranchへ安全に移し直す。", "主人公は通知機能の変更を、誤って別の作業branchへcommitした。正しいbranchに変更がないため、QA担当はレビューを始められない。先輩は、共有前に二つのbranchを正しい位置へ戻すよう主人公へ依頼した。",
            "通知機能のcommitはfeature/profileにある。未公開のうちにfeature/notificationへ移し、profileを元の位置へ戻すこと。", "通知機能の変更をfeature/notificationへ移し、feature/profileを変更前のC0へ戻す。最後にfeature/notificationをcheckoutした状態にする。",
            "git status / git log --oneline --all --decorate / git branch / git show <12または40桁ID> / git switch <feature/profile|feature/notification> / git cherry-pick <12または40桁ID> / git reset --hard <12または40桁ID>",
            new StageOutcome("未公開の通知機能commitがfeature/profileに置かれ、正しいfeature/notificationにはまだありませんでした。",
                    "feature/notificationが通知機能を持つ新しいcommitを指し、feature/profileは元のC0へ戻り、作業ツリーもcleanになりました。",
                    "通知機能を先にcherry-pickしてからprofileを戻すことで、必要な変更を失わずにbranchの役割を戻せます。",
                    "通知機能を移す前にfeature/profileをresetすると、必要なcommitを参照しにくくなります。誤ったbranchへ残したまま共有するのも、後の統合を複雑にします。",
                    "通知機能を正しいbranchへ移し、誤ったbranchを元へ戻せたと判断するには、二つのbranchの位置と作業ツリーのどこを確認すべきでしょうか？",
                    "feature/notificationが通知機能を持つ新しいcommitを指し、feature/profileが元のC0へ戻り、作業ツリーがcleanであることを確認します。",
                    "QA担当がレビューを再開すると、先輩は「なぜ二つのbranchがこの位置で安全なのか、君から説明して」と主人公に任せた。",
                    "主人公は、commitの内容だけでなく、branch位置の安全性をQA担当へ説明する役割を任された。"),
            StagePresentationPolicy.conceptOnlyBasic("状態確認", "branch比較", "commit確認", "変更移動", "履歴修正"),
            learningCard("2つのbranchの位置と、通知機能commitがどこにあるかを比較する。",
                    "通知機能を失わず、2つのbranchの役割を戻すには何を先に判断する？",
                    List.of("変更を正しいbranchへ残した後、誤ったbranchを戻す", "先に誤ったbranchを戻してから変更の所在を考える"),
                    "通知機能を正しいbranchへ移し、誤ったbranchを元の位置へ戻して、最後の作業位置も確認できた。",
                    "QAへ、どのbranch位置ならレビューを再開できると報告する？",
                    List.of("通知機能のbranchと元のbranchの位置をそれぞれ説明する", "通知機能の内容だけを説明する")));
    private static final StageDefinition STAGE_THREE = new StageDefinition("STAGE-GIT-03", "第3現場 / 作業中のbranch移動", "未commitの作業を正しいbranchへ移す",
            "mainで始めた検索機能の作業を失わずに、feature/searchへ移す。", "同期と検索機能を分担している最中、主人公はmain上で作業を始めてしまった。同期との共同作業をfeature/searchで続けたいが、変更はまだcommitできる段階ではない。作業を失わず、正しいbranchへ移そう。",
            "検索機能の未commit変更がmainに残っている。作業を一時退避し、feature/searchで復元してmainをきれいに戻すこと。", "検索機能の変更をfeature/searchへ未commitのまま移し、stashを残さない。",
            "観察 / 一時退避 / branch移動",
            new StageOutcome("main上の未commitな検索機能の変更が、正しいfeature/searchではなく作業ツリーに残っていました。",
                    "mainとfeature/searchのcommit位置を変えずに、検索機能の変更だけがfeature/searchの作業ツリーへ戻り、stashは空になりました。",
                    "stashで作業中の変更を一時退避してからbranchを切り替えると、commitや共有branchを急いで書き換えずに作業を運べます。",
                    "変更を残したままbranchを切り替えようとすると、別branchの変更と衝突したり、意図しない場所へ作業を持ち込んだりします。",
                    "検索機能の作業を失わず正しいbranchへ移せたと判断するには、branch、作業ツリー、index、stashの何を確認すべきでしょうか？",
                    "feature/search上で検索機能の変更だけが未commitで残り、indexは空、mainとfeature/searchのcommit位置は変わらず、stashも空であることを確認します。",
                    "同期がfeature/searchの状態を確認すると、先輩は「急いでcommitせず、状況を整理してから運べたね。次の作業段取りは任せる」と主人公に告げた。",
                    "主人公は、作業中の変更を失わずに整理し、次の作業段取りを任されるようになった。"),
            StagePresentationPolicy.conceptOnlyOff("観察", "一時退避", "branch移動"),
            learningCard("mainの未commit変更、index、移動先branch、stashの状態を確認する。",
                    "まだcommitできない作業を失わず、正しいbranchへ運ぶには何を選ぶ？",
                    List.of("作業を一時退避してbranchを移動し、そこで復元する", "main上で急いでcommitしてから移動する"),
                    "commit位置を変えず、移動先の作業ツリーへ変更を復元し、indexとstashの状態も整理できた。",
                    "同期へ、次にどの段取りで作業を続けると報告する？",
                    List.of("変更を確認してcommit・レビューへ進む順序を説明する", "とにかく先にcommitすると伝える")));
    private static final StageDefinition STAGE_FOUR = new StageDefinition("STAGE-GIT-04", "第4現場 / チーム間の変更衝突", "コンフリクトを解消して統合する",
            "二つのチームの意図を残して、競合したメッセージ定義をmainへ統合する。", "プロフィール画面の文言を、運用チームと機能チームが同じ行で変更した。QA担当は片方の要件だけではリリース確認を承認できない。主人公は、双方の意図を残してmainへ統合する必要がある。",
            "mainはsecurity settings、feature/profile-messageはpublic profileの案内を必要としている。競合を解消し、双方を含むmerge commitを完成させること。",
            "messages.propertiesを限定編集し、両方の要件を残したmerge commitをmainへ作る。",
            "git status / git log --oneline --all --decorate --graph / git diff / git branch / git merge feature/profile-message / git add src/main/resources/messages.properties / git commit --no-edit",
            new StageOutcome("mainとfeature/profile-messageが、同じプロフィール説明文を異なる目的で変更していました。",
                    "mainに2親のmerge commitが作られ、security settingsとpublic profileの両方を案内する文言になりました。",
                    "片方を選ぶのではなく要件を統合し、merge commitで二つの履歴を残すと、変更の由来と解決内容を追跡できます。",
                    "oursまたはtheirsだけを採用すると、一方のチームが必要とする案内が失われます。単一親commitでは統合履歴も残りません。",
                    "双方の意図を残して統合できたと判断するには、commitの親、ファイル内容、作業ツリーの何を確認すべきでしょうか？",
                    "merge commitがmainとfeatureの両方を直接parentに持ち、期待する統合文言と一致し、競合状態・index・作業ツリーがcleanであることを確認します。",
                    "QA担当が双方の要件を満たすと確認すると、先輩は「二つのチームへの解消説明は君に任せる」と主人公に告げた。",
                    "主人公は、コンフリクトを他者の意図を統合する作業として扱い、その判断を両チームへ説明する役割を任された。"),
            StagePresentationPolicy.conceptOnlyBasic("状態確認", "履歴比較", "競合確認", "限定編集", "統合確定"),
            learningCard("競合箇所と、運用チーム・機能チームそれぞれの受入条件を確認する。",
                    "競合を解消するとき、最初に守るべき判断はどれ？",
                    List.of("片方を捨てず、双方の要件を満たす内容へ統合する", "oursかtheirsの一方をそのまま採用する"),
                    "2つの親を持つ統合履歴と、双方の要件を満たすファイル内容を確認できた。",
                    "両チームへ、どの要件を残して統合したと報告する？",
                    List.of("運用と機能の両方の要件を残した理由を説明する", "競合markerを消したことだけを説明する")));
    private static final StageDefinition STAGE_FIVE = new StageDefinition("STAGE-GIT-05", "第5現場 / 消えたretry設定", "reflogから失われたcommitを復旧する",
            "削除されたbranchの元commitを、操作履歴から見つけて復旧する。", "対応終了の連絡を受けた同期が、決済APIのretry設定を持つfeature/payment-retryを削除してしまった。通常の履歴には見えないが、復旧の手掛かりは操作履歴に残っている。主人公はmainを動かさず、再検証できるbranchを戻すよう依頼される。",
            "feature/payment-retryは削除され、mainはC0に留まっている。HEAD reflogから元のC1を特定し、mainを変えずにbranchを復旧してそのbranchへ移動すること。", "feature/payment-retryを元のC1で復旧し、mainをC0のまま保ってそのbranchへ移動する。",
            "状態確認 / 通常履歴 / 操作履歴 / commit確認 / branch復旧",
            new StageOutcome("決済APIのretry設定を含むfeature/payment-retryが削除され、通常のrefから元commitへ到達できなくなっていました。",
                    "feature/payment-retryが元のC1を指し、mainはC0のまま保たれた状態で、主人公は復旧したbranchへ移動しました。",
                    "reflogで操作履歴に残るcommitを確認してからbranchを復元すると、mainや共有履歴を動かさずに必要な作業地点を戻せます。",
                    "mainをC1へ動かすと共有branchの役割を壊します。根拠を確認せず似たIDへbranchを作ると、誤った設定を再検証してしまいます。",
                    "復旧したbranchが元のC1を指し、mainがC0から動いていないことを、どの状態から説明できるでしょうか？",
                    "feature/payment-retryがreflogで確認したC1を指し、mainがC0のまま、作業ツリーがcleanであることを確認します。",
                    "主人公が復旧根拠を運用担当へ説明すると、同期は安堵する。先輩は「今回はコマンドだけでなく、根拠から復旧を説明できた。次のインシデント説明は君に任せる」と告げた。",
                    "主人公は、復旧操作だけでなく、その根拠を運用担当へ伝える役割を任された。"),
            StagePresentationPolicy.conceptOnlyRedactedBranches("状態確認", "通常履歴", "操作履歴", "commit確認", "branch復旧"),
            learningCard("通常のbranch一覧にない作業の痕跡と、操作履歴に残る候補の内容を確認する。",
                    "元の成果物だと判断できる根拠を集めるには、何を結び付ける？",
                    List.of("mainを変えていないこと、操作履歴、内容の一致を確認する", "似た内容を新しいcommitとして作り直す"),
                    "元の成果物をbranchとして復旧し、mainを変えていないことと作業ツリーの状態を確認できた。",
                    "運用担当へ、元の成果物だと説明する根拠は何か？",
                    List.of("操作履歴・内容・復旧後のbranch位置をつなげて説明する", "同じような内容になったとだけ説明する")));
    private static final StageDefinition TRAINING_ONE = new StageDefinition("TRAINING-GIT-01", "Git基礎研修 / 1", "最初の変更を記録する",
            "変更を観察し、次のcommitへ含めてlocal履歴へ記録する。", "入社初日の研修repositoryで、用意された案内文の変更を確認します。",
            "案内文の変更内容を確認し、最初の研修commitとして記録すること。", "変更をcommitし、mainの作業ツリーをcleanにする。",
            "状態確認 / 差分確認 / commit対象の選択 / 記録確認",
            new StageOutcome("案内文の変更がworking treeにあり、まだ履歴へ記録されていませんでした。",
                    "変更が初期mainの次のcommitとして記録され、working treeとindexがcleanになりました。",
                    "commit前にworking treeとindexを確認すると、何を履歴へ記録するかを自分で確かめられます。",
                    "確認せずに変更をまとめてstageすると、意図しない内容を履歴へ含めるおそれがあります。",
                    "working tree、index、HEADのどこが変わったと説明できますか？",
                    "変更はworking treeからindexへ選ばれ、commitによってHEADが新しい記録を指しました。",
                    "研修担当は最初のcommitを確認し、次は内容の選別も任せると伝えた。",
                    "主人公は、最初の研修commitを自分で確認して記録した。"),
            StagePresentationPolicy.conceptOnlyBasic("状態確認", "差分確認", "stage", "commit"));
    private static final StageDefinition TRAINING_TWO = new StageDefinition("TRAINING-GIT-02", "Git基礎研修 / 2", "commitに含めるものを選ぶ",
            "成果物だけをcommitし、再生成できるreportを履歴から除外する。", "同期と一緒に、研修成果物と生成物の違いを確認します。",
            "設定fileとignore規則は届ける。再生成できるbuild reportはcommitせず、workspaceには残すこと。", "設定とignore規則だけをcommitし、build reportをignoreする。",
            "状態確認 / staged差分 / stage解除 / commit対象の選択",
            new StageOutcome("再生成できるbuild reportまでindexへ入っていました。",
                    "設定とignore規則だけがcommitされ、build reportはworkspaceに残ったままignoreされました。",
                    "commit前にindexを点検すれば、成果物と一時生成物を分けて履歴を保てます。",
                    "stage済みという理由だけで全てcommitすると、再生成物が履歴へ蓄積します。",
                    "どのfileをHEADへ記録し、どのfileをworkspaceだけへ残しましたか？",
                    "設定とignore規則をHEADへ記録し、reportはignore対象としてworkspaceだけへ残しました。",
                    "同期は判断理由を理解し、次の模擬タスクの準備を主人公へ任せた。",
                    "主人公は、stage済みの内容も点検して選び直せるようになった。"),
            StagePresentationPolicy.conceptOnlyBasic("状態確認", "index確認", "stage解除", "選択commit"));
    private static final StageDefinition TRAINING_THREE = new StageDefinition("TRAINING-GIT-03", "Git基礎研修 / 3", "作業branchで変更する",
            "main上の未commit変更を失わず、作業branchへ移して記録する。", "仮配属前の模擬タスクで、main上の引継ぎ文書を作業branchへ移します。",
            "mainを動かさず、未commitの引継ぎ文書をfeature/onboardingへ移してcommitすること。", "feature/onboardingに変更を記録し、mainを初期位置のまま保つ。",
            "状態確認 / branch確認 / 作業branch作成 / commit",
            new StageOutcome("引継ぎ文書の未commit変更がmain上に残っていました。",
                    "mainを動かさず、feature/onboardingに変更を持つ新しいcommitを作成できました。",
                    "未commit変更を保ったまま作業branchを作ると、mainを汚さず作業単位を分けられます。",
                    "mainへ直接commitすると、仮配属先でレビューする作業境界が曖昧になります。",
                    "mainとfeature/onboardingは最終的にどの位置を指していますか？",
                    "mainは初期tipのまま、feature/onboardingはその直接の子commitを指しています。",
                    "研修担当は仮配属の準備が整ったと告げ、引継ぎ連絡を主人公へ渡した。",
                    "主人公は、mainを守る作業境界を自分で作れるようになった。"),
            StagePresentationPolicy.conceptOnlyBasic("状態確認", "branch確認", "branch作成", "commit"));
    private static final Map<String, StageDefinition> DEFINITIONS = Map.of(
            STAGE_ONE.key(), STAGE_ONE, STAGE_TWO.key(), STAGE_TWO, STAGE_THREE.key(), STAGE_THREE, STAGE_FOUR.key(), STAGE_FOUR,
            STAGE_FIVE.key(), STAGE_FIVE, TRAINING_ONE.key(), TRAINING_ONE, TRAINING_TWO.key(), TRAINING_TWO,
            TRAINING_THREE.key(), TRAINING_THREE);

    List<StageDefinition> definitions() {
        return List.of(STAGE_ONE, STAGE_TWO, STAGE_THREE, STAGE_FOUR, STAGE_FIVE);
    }

    List<StageDefinition> trainingDefinitions() {
        return List.of(TRAINING_ONE, TRAINING_TWO, TRAINING_THREE);
    }

    StageDefinition definition(String stageKey) {
        StageDefinition definition = DEFINITIONS.get(stageKey);
        if (definition == null) throw new IllegalArgumentException("unknown stage");
        return definition;
    }

    private static StageLearningCard learningCard(String evidence, String decisionPrompt, List<String> decisionOptions,
                                                   String result, String reportPrompt, List<String> reportOptions) {
        return new StageLearningCard(evidence, decisionPrompt, decisionOptions, result, reportPrompt, reportOptions);
    }
}
