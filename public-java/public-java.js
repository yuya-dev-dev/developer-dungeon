(function () {
  "use strict";

  const STORAGE_KEY = "developer-dungeon.public-java.progress.v1";
  const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
  const REFERENCE_PATTERN = /^[A-Z][A-Za-z0-9]*\.java$/;
  const STATUSES = Object.freeze({
    NOT_STARTED: "未着手",
    IN_PROGRESS: "学習中",
    COMPLETED: "完了"
  });
  const LEVELS = Object.freeze([
    { key: "BEGINNER", name: "BEGINNER", label: "初級", description: "クラス数・フィールド・メソッドの指定に沿って、設計の型を身につけます。" },
    { key: "INTERMEDIATE", name: "INTERMEDIATE", label: "中級", description: "同じ題材を広げ、自分で責務とクラス構成を判断します。" },
    { key: "ADVANCED", name: "ADVANCED", label: "上級", description: "例外や履歴、拡張性まで含め、実務に近い設計判断へ進みます。" }
  ]);
  const siteRoot = new URL("./", document.baseURI);
  let memoryProgress = {};
  let storageAvailable = true;
  let catalogSlugs = new Set();

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = String(text);
    return node;
  }

  function appendList(parent, items, ordered) {
    const list = element(ordered ? "ol" : "ul");
    items.forEach((item) => list.append(element("li", "", item)));
    parent.append(list);
  }

  function isTextArray(value) {
    return Array.isArray(value) && value.every((item) => typeof item === "string");
  }

  async function fetchJson(url) {
    const response = await fetch(url, { credentials: "same-origin", cache: "no-cache" });
    if (!response.ok) throw new Error("公開データを取得できませんでした。");
    return response.json();
  }

  function validateCatalog(value) {
    if (!value || !Array.isArray(value.directories) || value.directories.length !== 9) {
      throw new Error("問題一覧の形式が正しくありません。");
    }
    const directories = value.directories.slice();
    if (directories.some((slug) => typeof slug !== "string" || !SLUG_PATTERN.test(slug))) {
      throw new Error("問題一覧に不正な識別子があります。");
    }
    if (new Set(directories).size !== directories.length) {
      throw new Error("問題一覧に重複があります。");
    }
    return directories;
  }

  function validateProblem(problem, expectedSlug) {
    const textFields = ["key", "slug", "theme", "difficulty", "title", "summary"];
    const arrayFields = ["learningObjectives", "prerequisites", "requirements", "constraints", "mandatoryRequirements", "optionalRequirements", "designPoints", "hints", "referenceFiles"];
    if (!problem || textFields.some((name) => typeof problem[name] !== "string")) throw new Error("問題データの形式が正しくありません。");
    if (problem.slug !== expectedSlug || !SLUG_PATTERN.test(problem.slug)) throw new Error("問題識別子が一致しません。");
    if (!LEVELS.some((level) => level.key === problem.difficulty)) throw new Error("難易度が正しくありません。");
    if (!Number.isInteger(problem.order) || problem.order < 1 || problem.order > 99) throw new Error("問題番号が正しくありません。");
    if (arrayFields.some((name) => !isTextArray(problem[name]))) throw new Error("問題データの項目が正しくありません。");
    if (!problem.mainScenario || ["instances", "steps", "expectedResults", "invariants"]
        .some((name) => !isTextArray(problem.mainScenario[name]) || problem.mainScenario[name].length === 0)) {
      throw new Error("Main動作確認の形式が正しくありません。");
    }
    if (problem.referenceFiles.length === 0 || problem.referenceFiles.some((name) => !REFERENCE_PATTERN.test(name))) throw new Error("模範コードのファイル名が正しくありません。");
    return problem;
  }

  function normalizeProgress(value) {
    if (!value || typeof value !== "object" || Array.isArray(value)) return {};
    const clean = {};
    Object.entries(value).forEach(([slug, status]) => {
      if (catalogSlugs.has(slug) && Object.hasOwn(STATUSES, status)) clean[slug] = status;
    });
    return clean;
  }

  function readProgress() {
    if (!storageAvailable) return memoryProgress;
    let stored;
    try {
      stored = localStorage.getItem(STORAGE_KEY);
    } catch (_error) {
      storageAvailable = false;
      memoryProgress = {};
      return memoryProgress;
    }
    let parsed = {};
    let needsCleanup = false;
    if (stored) {
      try {
        parsed = JSON.parse(stored);
      } catch (_error) {
        needsCleanup = true;
      }
    }
    memoryProgress = normalizeProgress(parsed);
    needsCleanup = needsCleanup || JSON.stringify(parsed) !== JSON.stringify(memoryProgress);
    if (stored && needsCleanup) {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(memoryProgress));
      } catch (_error) {
        storageAvailable = false;
      }
    }
    return memoryProgress;
  }

  function saveProgress(slug, status) {
    const progress = Object.assign({}, readProgress(), { [slug]: status });
    memoryProgress = progress;
    if (storageAvailable) {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(progress));
      } catch (_error) {
        storageAvailable = false;
      }
    }
  }

  function statusFor(slug) {
    return readProgress()[slug] || "NOT_STARTED";
  }

  function showMessage(text, isError) {
    const message = document.getElementById("page-message");
    if (!message) return;
    message.hidden = false;
    message.textContent = text;
    message.classList.toggle("problem-load-error", Boolean(isError));
  }

  function hideMessage() {
    const message = document.getElementById("page-message");
    if (message) message.hidden = true;
  }

  function renderProblemCard(problem) {
    const link = element("a", "problem-card");
    link.href = new URL(`problem.html?slug=${encodeURIComponent(problem.slug)}`, siteRoot).href;
    link.append(element("span", "problem-order", String(problem.order).padStart(2, "0")));
    link.append(element("span", "problem-theme", problem.theme));
    link.append(element("strong", "", problem.title));
    link.append(element("p", "", problem.summary));
    const status = statusFor(problem.slug);
    link.append(element("span", `problem-status status-${status.toLowerCase()}`, STATUSES[status]));
    link.append(element("span", "card-arrow", "↗"));
    return link;
  }

  async function renderList() {
    try {
      const directories = validateCatalog(await fetchJson(new URL("data/catalog.json", siteRoot)));
      catalogSlugs = new Set(directories);
      const problems = await Promise.all(directories.map(async (slug) => validateProblem(await fetchJson(new URL(`data/${slug}/problem.json`, siteRoot)), slug)));
      const root = document.getElementById("problem-groups");
      LEVELS.forEach((level) => {
        const section = element("section", "level-section");
        const heading = element("div", "level-heading");
        const titleBlock = element("div");
        titleBlock.append(element("p", "eyebrow", level.name), element("h2", "", level.label));
        heading.append(titleBlock, element("p", "", level.description));
        const grid = element("div", "problem-grid");
        problems.filter((problem) => problem.difficulty === level.key).sort((a, b) => a.order - b.order).forEach((problem) => grid.append(renderProblemCard(problem)));
        section.append(heading, grid);
        root.append(section);
      });
      hideMessage();
    } catch (_error) {
      showMessage("問題一覧を読み込めませんでした。時間をおいて再度お試しください。", true);
    }
  }

  function contentSection(title, items, options) {
    const section = element("section", `content-card${options && options.accent ? " accent-card" : ""}`);
    section.append(element("h2", "", title));
    appendList(section, items, Boolean(options && options.ordered));
    return section;
  }

  function renderScaffold(scaffold) {
    if (!scaffold || !Number.isInteger(scaffold.classCount) || !Array.isArray(scaffold.classes)) return null;
    const section = element("section", "content-card");
    const heading = element("div", "section-heading");
    const headingText = element("div");
    headingText.append(element("p", "eyebrow", "BEGINNER SCAFFOLD"), element("h2", "", "指定された設計の骨格"));
    heading.append(headingText, element("span", "", `全 ${scaffold.classCount} class`));
    const grid = element("div", "scaffold-grid");
    scaffold.classes.forEach((spec) => {
      const article = element("article");
      article.append(element("h3", "", spec.name), element("p", "", spec.purpose));
      article.append(element("b", "", `constructor ${spec.constructorCount}個 / field ${spec.fieldCount}個 / method ${spec.methodCount}個`));
      [["Constructor", spec.constructors], ["Field", spec.fields], ["Method（constructorを除く）", spec.methods]].forEach(([label, items]) => {
        article.append(element("h4", "", label));
        appendList(article, Array.isArray(items) ? items : [], false);
      });
      grid.append(article);
    });
    section.append(heading, grid);
    return section;
  }

  function renderDetails(title, items) {
    const details = element("details", "content-card");
    details.append(element("summary", "", title));
    appendList(details, items, false);
    return details;
  }

  function renderMainScenario(scenario) {
    const section = element("section", "content-card main-scenario-card");
    const heading = element("div", "section-heading");
    const headingText = element("div");
    headingText.append(element("p", "eyebrow", "MAIN SCENARIO"), element("h2", "", "Mainメソッドで動作を確認する"));
    heading.append(headingText, element("span", "", "必須"));
    section.append(heading, element("p", "", "設計したクラスをMainから利用し、正常系と失敗系の両方を確認してください。Mainクラスとmainメソッドは、初級のクラス数・constructor数・field数・method数に含みません。中級・上級は、同等の外部動作を実現できれば模範例と異なるAPI・責務分割でも構いません。"));
    const grid = element("div", "main-scenario-grid");
    [["生成するインスタンス", scenario.instances, false], ["実行する操作", scenario.steps, true],
      ["期待する結果", scenario.expectedResults, false], ["失敗後に守る状態", scenario.invariants, false]]
      .forEach(([title, items, ordered]) => {
        const article = element("article");
        article.append(element("h3", "", title));
        appendList(article, items, ordered);
        grid.append(article);
      });
    section.append(grid);
    return section;
  }

  async function renderReferences(container, problem) {
    const section = element("section", "reference-section");
    section.id = "reference";
    const heading = element("div");
    heading.append(element("p", "eyebrow", "REFERENCE DESIGN"), element("h2", "", "模範設計例"), element("p", "", "これは唯一解ではありません。自分の設計と責務の置き方を比較してみましょう。"));
    section.append(heading);
    for (const fileName of problem.referenceFiles) {
      const response = await fetch(new URL(`data/${problem.slug}/reference/${fileName}`, siteRoot), { credentials: "same-origin", cache: "no-cache" });
      if (!response.ok) throw new Error("模範コードを取得できませんでした。");
      const source = await response.text();
      if (source.length > 65536) throw new Error("模範コードが大きすぎます。");
      const details = element("details", "reference-file");
      details.append(element("summary", "", fileName));
      const pre = element("pre");
      pre.append(element("code", "", source));
      details.append(pre);
      section.append(details);
    }
    container.append(section);
  }

  function updateProgressUi(slug) {
    const status = statusFor(slug);
    document.getElementById("progress-label").textContent = STATUSES[status];
    document.getElementById("progress-status").value = status;
    const note = document.getElementById("storage-note");
    note.textContent = storageAvailable
      ? "進捗は、この端末・このブラウザだけに保存されます。"
      : "ブラウザ保存を利用できないため、この画面を閉じると進捗は消えます。";
  }

  async function renderDetail() {
    const content = document.getElementById("problem-content");
    try {
      const slug = new URLSearchParams(location.search).get("slug") || "";
      if (!SLUG_PATTERN.test(slug)) throw new Error("指定された問題はありません。");
      const directories = validateCatalog(await fetchJson(new URL("data/catalog.json", siteRoot)));
      catalogSlugs = new Set(directories);
      if (!directories.includes(slug)) throw new Error("指定された問題はありません。");
      const problem = validateProblem(await fetchJson(new URL(`data/${slug}/problem.json`, siteRoot)), slug);
      document.title = `${problem.title} | Javaクラス設計演習`;

      const hero = element("header", "problem-hero");
      const level = LEVELS.find((item) => item.key === problem.difficulty);
      hero.append(element("p", "eyebrow", `${level.label} ・ ${problem.theme}`), element("h1", "", problem.title), element("p", "", problem.summary));
      content.append(hero);
      content.append(contentSection("この問題で身につけること", problem.learningObjectives, { accent: true }));

      const firstColumns = element("div", "content-columns");
      firstColumns.append(contentSection("前提知識", problem.prerequisites), contentSection("実装条件", problem.constraints));
      content.append(firstColumns, contentSection("要求仕様", problem.requirements, { ordered: true }));
      const scaffold = renderScaffold(problem.beginnerScaffold);
      if (scaffold) content.append(scaffold);

      const secondColumns = element("div", "content-columns");
      secondColumns.append(contentSection("必須要件", problem.mandatoryRequirements), contentSection("発展要件", problem.optionalRequirements));
      content.append(secondColumns, contentSection("設計時に考えるポイント", problem.designPoints), renderMainScenario(problem.mainScenario), renderDetails("ヒントを見る", problem.hints));
      await renderReferences(content, problem);

      updateProgressUi(slug);
      document.getElementById("save-progress").addEventListener("click", () => {
        const selected = document.getElementById("progress-status").value;
        if (!Object.hasOwn(STATUSES, selected)) return;
        saveProgress(slug, selected);
        updateProgressUi(slug);
      });
      hideMessage();
    } catch (_error) {
      showMessage("指定された問題を開けませんでした。問題一覧から選び直してください。", true);
      const back = element("a", "", "問題一覧へ戻る");
      back.href = new URL("problems.html", siteRoot).href;
      content.append(back);
      document.querySelector(".progress-panel").hidden = true;
    }
  }

  const page = document.body.dataset.page;
  if (page === "list") renderList();
  if (page === "detail") renderDetail();
}());
