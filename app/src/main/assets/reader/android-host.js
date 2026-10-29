// Connects the desktop reader page (reader.js, unchanged) to the Android app.
//
// reader.js posts to its host through window.webkit.messageHandlers.leanmark,
// as on the desktop WebKit hosts, and receives through LeanMarkHost.receive.
// Here both directions travel over a MessagePort that the app hands to the
// page once it has loaded. Messages posted before that wait in a queue.
(function () {
  "use strict";

  var port = null;
  var queue = [];

  // reader.js uses Element.replaceChildren, which WebView has from version 86.
  // Older WebView builds (69 on the oldest Android 8 images) get this
  // equivalent, so the page works there too.
  if (!Element.prototype.replaceChildren) {
    Element.prototype.replaceChildren = function () {
      while (this.firstChild) {
        this.removeChild(this.firstChild);
      }
      this.append.apply(this, arguments);
    };
  }

  function send(message) {
    var text = String(message);
    if (port) {
      port.postMessage(text);
    } else {
      queue.push(text);
    }
  }

  window.webkit = Object.freeze({
    messageHandlers: Object.freeze({
      leanmark: Object.freeze({ postMessage: send })
    })
  });

  // The back gesture closes an open figure viewer, the way Escape does.
  function closeViewer() {
    document.dispatchEvent(new KeyboardEvent("keydown", {
      key: "Escape",
      bubbles: true,
      cancelable: true
    }));
  }

  function receive(event) {
    var data;
    try {
      data = JSON.parse(event.data);
    } catch (_error) {
      return;
    }
    if (data && data.type === "android-back") {
      closeViewer();
    } else if (window.LeanMarkHost) {
      window.LeanMarkHost.receive(data);
    }
  }

  // The app posts exactly one port, right after the page loads. The page has
  // no frames and runs no document scripts, so nothing else can post here.
  window.addEventListener("message", function (event) {
    if (port || event.data !== "leanmark-port" || !event.ports ||
        event.ports.length !== 1) {
      return;
    }
    port = event.ports[0];
    port.onmessage = receive;
    queue.splice(0).forEach(function (message) {
      port.postMessage(message);
    });
  });

  // reader.js marks each finished render on the root element. The app uses the
  // first one to report when the document is on screen.
  new MutationObserver(function () {
    var state = document.documentElement.dataset.renderState;
    if (state) {
      send("render|" + state);
    }
  }).observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["data-render-state"]
  });

  // The app needs to know when the figure viewer is open, so that back closes
  // it instead of leaving the app.
  document.addEventListener("DOMContentLoaded", function () {
    var viewer = document.getElementById("figureViewer");
    if (!viewer) {
      return;
    }
    new MutationObserver(function () {
      send(viewer.hidden ? "viewer|closed" : "viewer|open");
    }).observe(viewer, { attributes: true, attributeFilter: ["hidden"] });
  });
}());
