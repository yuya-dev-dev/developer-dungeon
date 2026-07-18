(function () {
  "use strict";

  var REGION_IDS = [
    "stage-header",
    "stage-sidebar-state",
    "stage-repository",
    "stage-workspace",
    "stage-clear-dialogue"
  ];
  var pending = false;

  function stageRoot(documentRoot) {
    return documentRoot.querySelector("[data-stage-key]");
  }

  function uniqueRegion(documentRoot, id) {
    var regions = documentRoot.querySelectorAll("#" + id + "[data-stage-region='" + id + "']");
    return regions.length === 1 ? regions[0] : null;
  }

  function validateResponseDocument(nextDocument, responseUrl) {
    var currentRoot = stageRoot(document);
    var nextRoot = stageRoot(nextDocument);
    var responseOrigin = new URL(responseUrl, window.location.href).origin;
    if (!currentRoot || !nextRoot || responseOrigin !== window.location.origin ||
        currentRoot.dataset.stageKey !== nextRoot.dataset.stageKey) {
      return null;
    }

    var regions = [];
    for (var index = 0; index < REGION_IDS.length; index += 1) {
      var id = REGION_IDS[index];
      var current = uniqueRegion(document, id);
      var next = uniqueRegion(nextDocument, id);
      if (!current || !next || next.querySelector("script")) {
        return null;
      }
      regions.push({current: current, next: next});
    }
    return regions;
  }

  function saveScrollPositions() {
    var positions = [];
    document.querySelectorAll("[data-scroll-key]").forEach(function (element) {
      positions.push({
        key: element.dataset.scrollKey,
        top: element.scrollTop,
        left: element.scrollLeft
      });
    });
    return {windowX: window.scrollX, windowY: window.scrollY, elements: positions};
  }

  function restoreScrollPositions(saved) {
    saved.elements.forEach(function (position) {
      var element = document.querySelector("[data-scroll-key='" + position.key + "']");
      if (element) {
        element.scrollTop = position.top;
        element.scrollLeft = position.left;
      }
    });
    window.scrollTo(saved.windowX, saved.windowY);
  }

  function focusAfterUpdate(action, wasCleared, isCleared) {
    var target;
    if (!wasCleared && isCleared) {
      var initialDialogue = document.querySelector("[data-dialogue-scene]");
      if (initialDialogue) {
        initialDialogue.remove();
      }
      target = document.getElementById("clear-heading");
      if (target) {
        target.focus({preventScroll: true});
        target.scrollIntoView({block: "center"});
      }
      return;
    }

    if (action === "hint") {
      target = document.getElementById("stage-sidebar-hint") || document.querySelector(".hint-button");
    } else if (action === "editor") {
      target = document.getElementById("stage-editor-feedback") || document.getElementById("stage-editor");
    } else {
      target = document.getElementById("stage-command-feedback") || document.getElementById("stage-workspace");
    }
    if (target) {
      target.focus({preventScroll: true});
    }
  }

  function showFailure() {
    var message = document.getElementById("partial-update-error");
    if (message) {
      message.hidden = false;
      message.focus({preventScroll: true});
    }
  }

  function setBusy(form, busy) {
    form.querySelectorAll("button[type='submit'], input[type='submit']").forEach(function (button) {
      button.disabled = busy;
      button.setAttribute("aria-busy", busy ? "true" : "false");
    });
  }

  async function submit(form) {
    var method = (form.method || "get").toUpperCase();
    var actionUrl = new URL(form.action, window.location.href);
    if (method !== "POST" || actionUrl.origin !== window.location.origin || pending) {
      return;
    }

    pending = true;
    setBusy(form, true);
    var savedScroll = saveScrollPositions();
    var wasCleared = Boolean(document.getElementById("clear-heading"));
    var action = form.dataset.stageForm || "command";

    try {
      var response = await window.fetch(actionUrl.href, {
        method: "POST",
        body: new FormData(form),
        credentials: "same-origin",
        headers: {Accept: "text/html"},
        redirect: "follow"
      });
      var contentType = response.headers.get("content-type") || "";
      if (!response.ok || !contentType.toLowerCase().startsWith("text/html") ||
          new URL(response.url).origin !== window.location.origin) {
        throw new Error("Unexpected stage response");
      }

      var html = await response.text();
      var nextDocument = new DOMParser().parseFromString(html, "text/html");
      var regions = validateResponseDocument(nextDocument, response.url);
      if (!regions) {
        throw new Error("Stage response contract mismatch");
      }

      regions.forEach(function (region) {
        region.current.replaceWith(document.importNode(region.next, true));
      });
      restoreScrollPositions(savedScroll);
      var isCleared = Boolean(document.getElementById("clear-heading"));
      focusAfterUpdate(action, wasCleared, isCleared);
    } catch (error) {
      showFailure();
      setBusy(form, false);
    } finally {
      pending = false;
    }
  }

  document.addEventListener("submit", function (event) {
    var form = event.target.closest("form[data-stage-form]");
    if (!form) {
      return;
    }
    event.preventDefault();
    submit(form);
  });
}());
