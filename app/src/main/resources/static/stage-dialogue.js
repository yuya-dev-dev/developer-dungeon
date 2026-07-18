(function () {
  "use strict";

  function failOpen(scene, beats, controls, fallback, replay, progress) {
    scene.hidden = false;
    scene.classList.remove("dialogue-enhanced");
    beats.forEach(function (beat) {
      beat.hidden = false;
      beat.removeAttribute("aria-current");
    });
    controls.hidden = true;
    fallback.hidden = false;
    replay.hidden = true;
    if (progress) {
      progress.hidden = true;
    }
  }

  function initialize(scene) {
    var beats = Array.from(scene.querySelectorAll("[data-dialogue-beat]"));
    var controls = scene.querySelector("[data-dialogue-controls]");
    var next = scene.querySelector("[data-dialogue-next]");
    var skip = scene.querySelector("[data-dialogue-skip]");
    var fallback = scene.querySelector("[data-dialogue-fallback]");
    var progress = scene.querySelector("[data-dialogue-progress]");
    var current = scene.querySelector("[data-dialogue-current]");
    var total = scene.querySelector("[data-dialogue-total]");
    var replay = document.querySelector("[data-dialogue-replay]");
    var mission = document.getElementById("mission-heading");
    var stageKey = scene.dataset.stageKey;

    if (!beats.length || !controls || !next || !skip || !fallback || !progress || !current || !total || !replay || !mission || !stageKey) {
      return;
    }

    var storageKey = "developer-dungeon:dialogue:v1:" + stageKey;
    var index = 0;

    function hasSeenDialogue() {
      try {
        return window.sessionStorage.getItem(storageKey) === "seen";
      } catch (error) {
        return false;
      }
    }

    function rememberDialogue() {
      try {
        window.sessionStorage.setItem(storageKey, "seen");
      } catch (error) {
        // Storage is optional. The scene still closes for the current page.
      }
    }

    function showBeat(nextIndex) {
      index = nextIndex;
      beats.forEach(function (beat, beatIndex) {
        var selected = beatIndex === index;
        beat.hidden = !selected;
        if (selected) {
          beat.setAttribute("aria-current", "step");
        } else {
          beat.removeAttribute("aria-current");
        }
      });
      current.textContent = String(index + 1);
      next.textContent = index === beats.length - 1 ? "対応を始める" : "次へ";
    }

    function closeDialogue() {
      rememberDialogue();
      scene.hidden = true;
      mission.focus();
    }

    function safeHandler(action) {
      return function (event) {
        try {
          action(event);
        } catch (error) {
          failOpen(scene, beats, controls, fallback, replay, progress);
        }
      };
    }

    next.addEventListener("click", safeHandler(function () {
      if (index < beats.length - 1) {
        showBeat(index + 1);
        next.focus();
      } else {
        closeDialogue();
      }
    }));
    skip.addEventListener("click", safeHandler(closeDialogue));
    scene.replayDialogue = safeHandler(function () {
      scene.hidden = false;
      showBeat(0);
      next.focus();
    });

    scene.classList.add("dialogue-enhanced");
    controls.hidden = false;
    fallback.hidden = true;
    replay.hidden = false;
    total.textContent = String(beats.length);
    progress.hidden = false;

    if (hasSeenDialogue()) {
      scene.hidden = true;
    } else {
      showBeat(0);
      next.focus();
    }
  }

  document.querySelectorAll("[data-dialogue-scene]").forEach(function (scene) {
    try {
      initialize(scene);
    } catch (error) {
      var beats = Array.from(scene.querySelectorAll("[data-dialogue-beat]"));
      var controls = scene.querySelector("[data-dialogue-controls]");
      var fallback = scene.querySelector("[data-dialogue-fallback]");
      var progress = scene.querySelector("[data-dialogue-progress]");
      var replay = document.querySelector("[data-dialogue-replay]");
      if (controls && fallback && replay) {
        failOpen(scene, beats, controls, fallback, replay, progress);
      }
    }
  });

  document.addEventListener("click", function (event) {
    var replay = event.target.closest("[data-dialogue-replay]");
    if (!replay) {
      return;
    }
    var scene = document.querySelector("[data-dialogue-scene]");
    if (scene && typeof scene.replayDialogue === "function") {
      scene.replayDialogue(event);
    }
  });
}());
