# Phase 3 ステージ一覧・進捗表示 実装計画

## 1. 目的と完成条件

Phase 3で永続化したSTAGE-GIT-01のclear状態と最高スターを、プレイ開始前に確認できるようにする。MVP要件の「ステージ一覧画面」と「プレイ画面」を分け、トップ画面を一覧、既存課題画面をプレイ画面として扱う。

今回の完成条件は、トップ画面でSTAGE-GIT-01の状態（未クリア／クリア済み）と最高スターを表示でき、app再起動後も同じ管理DBの値が表示されることである。

## 2. 実装範囲

- `GET /`で、STAGE-GIT-01専用のステージ一覧を表示する
- `GET /stages/STAGE-GIT-01`で、既存のプレイ画面を表示する
- 一覧には、ステージ名、短い目的、未クリア／クリア済み、最高スター、プレイ開始リンクを表示する
- 最高スターは既存の`StagePersistence.highestStars("STAGE-GIT-01")`から導出する
- `StageOneService`へ、Runnerを呼ばずworkspaceを作らない進捗読取専用のメソッドを追加する
- 一覧用Thymeleaf templateと、既存の`stage.css`に必要最小限のスタイルを追加する
- serviceとcontrollerの対象限定unit testを追加する

## 3. 実装しない範囲

- STAGE-GIT-02〜05の実装、ロック解除、次ステージ遷移
- login、player識別、ユーザー別進捗、ランキング
- 任意のstage keyを扱う汎用catalog、DB schema追加、migration追加
- プレイ画面のGit操作、採点、Runner契約、Docker／Compose設定の変更
- Browser E2E、全テスト実行

## 4. 境界と安全性

- 一覧表示はDBの最高スターqueryだけを実行し、`StageOneService.open()`を呼ばない。したがって一覧閲覧だけではRunnerやchallenge containerを起動しない。
- 進捗queryがDB障害などで失敗した場合は、0スターへ置換したり`open()`へフォールバックしたりせず、例外として扱う。保存済み進捗を未クリアと誤表示せず、Runnerやattemptを作らないことを優先する。
- stage keyはControllerとServiceで`STAGE-GIT-01`へ固定し、リクエスト値から受け取らない。
- 表示する文言、スター数、状態はThymeleafの通常エスケープで出力する。Git出力を一覧へ再表示しない。
- 既存のCSRF、CSP、POST endpoint、Runner／DB credential境界は変更しない。

## 5. 画面と遷移

```text
GET /                         -> stages.html（一覧、DB read-only）
  └─ GET /stages/STAGE-GIT-01 -> stage.html（既存の課題プレイ）
       ├─ POST /commands
       ├─ POST /hint
       └─ POST /reset
```

一覧のSTAGE-GIT-01カードは、最高スターが0なら「未クリア」、1〜3なら「クリア済み」と表示する。既存プレイ画面の操作結果と最高スターは、一覧へ戻ったときにDBから再読込する。

## 6. テスト

Docker不要の対象限定テストだけを実施する。

1. `StageOneServiceTest`：進捗読取が最高スターを返し、Runnerを呼ばないこと
2. `StageOneServiceTest`：進捗query失敗時にもRunnerとattempt lifecycleのPersistence操作を呼ばないこと
3. `StageControllerTest`：トップが`progress()`だけを呼び一覧templateとSTAGE-GIT-01の進捗modelを返すこと、プレイrouteが`open()`を呼ぶこと、未対応のstage routeが404でServiceを呼ばないこと

既存のPostgreSQL integration testは、`highestStars` queryとmigrationをPhase 3基盤PRで確認済みのため、今回再実行しない。Docker、Browser E2E、全テストは実行しない。
