/*
 * The call layer's Spine stand, rendered with the vendored spine-ts 3.6 runtime.
 *
 * This is a small port of the web client's StandEngine (frontend/src/components/
 * conversations/stand/StandEngine.ts): it loads one outfit, plays the tracks the
 * current direction asks for, frames her head to knees for a portrait call screen, and
 * works around spine-ts 3.6's clipping bug where the end slot has no attachment.
 */
(function () {
  "use strict";

  var canvas = document.getElementById("stand");
  var bridge = window.EidolonStand || null;

  var TRACK = { body: 0, face: 1, nod: 2, eye: 3, mouth: 5 };
  var MIX_SECONDS = 0.3;
  var MOUTH_MIX_SECONDS = 0.1;
  var MAX_STEP_SECONDS = 0.1;
  var MAX_PIXEL_RATIO = 2;
  var FRAMING_HEIGHT = 0.85;
  var MIN_WIDTH = 0.7;
  var HEADROOM = 0.03;
  var UNPACK_PREMULTIPLY_ALPHA_WEBGL = 0x9241;

  var BODY = {
    calm: ["wait"],
    happy: ["smile1", "smile2"],
    shy: ["shy1", "shy2", "smile2"],
    surprised: ["surp1", "surp2"],
    sad: ["sad1", "sad2"],
    angry: ["anger1", "anger2"],
    thinking: ["think", "think1", "think2", "wait4", "wait"],
  };
  var FACE = {
    calm: ["face_wait"],
    happy: ["face_smile", "face_smile1", "face_smile2"],
    shy: ["face_shy", "face_shy2"],
    surprised: ["face_surp"],
    sad: ["face_sad"],
    angry: ["face_anger", "face_anger1", "face_anger2"],
    thinking: ["face_serious"],
  };
  var LIP = {
    calm: "wait",
    happy: "smile",
    shy: "shy",
    surprised: "surp",
    sad: "sad",
    angry: "anger",
    thinking: "serious",
  };

  var spine = null;
  var gl = null;
  var renderer = null;
  var assets = null;
  var skeleton = null;
  var data = null;
  var state = null;
  var animations = new Map();
  var bounds = null;
  var clips = new Map();
  var shown = { mood: null, face: null, mouth: null, eye: null, nod: null };
  var direction = { mood: "calm", speaking: false, looking: false, nod: null };
  var paused = false;
  var lost = false;
  var disposed = false;
  var raf = 0;
  var last = 0;
  var lastDraw = 0;
  var loads = 0;
  var failed = false;

  function notifyReady() {
    canvas.style.opacity = "1";
    if (bridge && bridge.ready) bridge.ready();
  }

  function notifyFailed(message) {
    if (failed) return;
    failed = true;
    stop();
    if (bridge && bridge.failed) bridge.failed(String(message || "立绘加载失败"));
  }

  function pick(names) {
    for (var i = 0; i < names.length; i++) {
      if (animations.has(names[i])) return names[i];
    }
    return null;
  }

  function anyMouth() {
    var found = null;
    animations.forEach(function (duration, name) {
      if (found !== null) return;
      if (name.indexOf("lip_") === 0 && !/_s\d*$/.test(name) && name !== "lip_a" && duration > 0) {
        found = name;
      }
    });
    return found;
  }

  function pickAnimations() {
    var mood = direction.mood;
    var k = LIP[mood] || LIP.calm;
    var mouth = pick([
      "lip_" + k,
      "lip_" + k + "1",
      "lip_" + k + "2",
      "lip_smile",
      "lip_wait",
    ]) || anyMouth();
    var nodding = direction.nod && pick([direction.nod.kind, direction.nod.kind + "2"]);
    return {
      body: pick(BODY[mood] || BODY.calm) || "wait",
      face: pick(FACE[mood] || FACE.calm) || "blank",
      mouth: mouth ? { name: mouth, open: !!direction.speaking } : null,
      nod: direction.nod && nodding ? { name: nodding, id: direction.nod.id } : null,
      eye: direction.looking && animations.has("eye_front") ? "eye_front" : null,
    };
  }

  function animationEvent(name, eventName) {
    var animation = data.findAnimation(name);
    if (!animation || !animation.timelines) return null;
    for (var i = 0; i < animation.timelines.length; i++) {
      var timeline = animation.timelines[i];
      if (!(timeline instanceof spine.EventTimeline)) continue;
      var events = timeline.events || [];
      for (var j = 0; j < events.length; j++) {
        var event = events[j];
        if (event.data && event.data.name === eventName) {
          return {
            time: event.time,
            value: event.stringValue || (event.data && event.data.stringValue) || "",
          };
        }
      }
    }
    return null;
  }

  function playBody(name) {
    if (!animations.has(name)) return;
    var current = state.getCurrent(TRACK.body);
    if (current && current.animation && current.animation.name === name) return;
    var relay = current && current.animation
      ? animationEvent(current.animation.name, "relay")
      : null;
    var entry;
    if (relay && animations.has(relay.value)) {
      state.setAnimation(TRACK.body, relay.value, false);
      entry = state.addAnimation(TRACK.body, name, false, 0);
    } else {
      entry = state.setAnimation(TRACK.body, name, false);
    }
    var loopStart = animationEvent(name, "loop_start");
    if (!loopStart) {
      entry.loop = true;
      return;
    }
    entry.listener = {
      complete: function (played) {
        if (played.loop) return;
        played.loop = true;
        played.animationStart = loopStart.time;
        played.trackTime = 0;
      },
    };
  }

  function follow() {
    if (!state) return;
    var want = pickAnimations();
    if (shown.mood !== direction.mood) {
      shown.mood = direction.mood;
      playBody(want.body);
    }
    if (shown.face !== want.face) {
      shown.face = want.face;
      if (animations.has(want.face)) state.setAnimation(TRACK.face, want.face, true);
      else state.setEmptyAnimation(TRACK.face, MIX_SECONDS);
    }
    var mouth = want.mouth && (want.mouth.name + " " + (want.mouth.open ? "open" : "shut"));
    if (want.mouth && shown.mouth !== mouth) {
      shown.mouth = mouth;
      var entry = state.setAnimation(TRACK.mouth, want.mouth.name, want.mouth.open);
      entry.mixDuration = MOUTH_MIX_SECONDS;
      if (!want.mouth.open) entry.animationEnd = 0;
    }
    if (shown.eye !== want.eye) {
      shown.eye = want.eye;
      if (want.eye) state.setAnimation(TRACK.eye, want.eye, true);
      else state.setEmptyAnimation(TRACK.eye, MIX_SECONDS);
    }
    if (want.nod && shown.nod !== want.nod.id) {
      shown.nod = want.nod.id;
      var nod = state.setAnimation(TRACK.nod, want.nod.name, false);
      state.addEmptyAnimation(TRACK.nod, MIX_SECONDS, nod.animation.duration);
    }
  }

  function read(outfitUrl) {
    var loader = new spine.AtlasAttachmentLoader(assets.get(outfitUrl + "data.atlas"));
    data = new spine.SkeletonJson(loader).readSkeletonData(assets.get(outfitUrl + "data.json"));
    skeleton = new spine.Skeleton(data);
    if (data.findSkin("normal")) skeleton.setSkinByName("normal");
    skeleton.setToSetupPose();
    skeleton.updateWorldTransform();
    var offset = new spine.Vector2();
    var size = new spine.Vector2();
    skeleton.getBounds(offset, size, []);
    bounds = { x: offset.x, y: offset.y, width: size.x, height: size.y };
    var stateData = new spine.AnimationStateData(data);
    stateData.defaultMix = MIX_SECONDS;
    state = new spine.AnimationState(stateData);
    animations = new Map();
    data.animations.forEach(function (animation) {
      animations.set(animation.name, animation.duration);
    });
    clips = new Map();
    shown = { mood: null, face: null, mouth: null, eye: null, nod: null };
    follow();
    start();
    notifyReady();
  }

  function load(outfitUrl) {
    if (!/^https?:\/\//i.test(outfitUrl)) {
      notifyFailed("立绘地址无效");
      return;
    }
    var ticket = ++loads;
    failed = false;
    var json = outfitUrl + "data.json";
    var atlas = outfitUrl + "data.atlas";
    var settled = 0;
    var error = null;

    function ready() {
      settled++;
      if (settled < 2) return;
      if (error) {
        notifyFailed(error);
        return;
      }
      if (ticket !== loads || disposed) return;
      try {
        read(outfitUrl);
      } catch (e) {
        notifyFailed(e && e.message ? e.message : "立绘数据读取失败");
      }
    }

    function fail(message) {
      if (!error) error = message || "立绘素材下载失败";
      ready();
    }

    assets.loadText(json, ready, function (_, message) { fail(message); });
    assets.loadTextureAtlas(atlas, ready, function (_, message) { fail(message); });
  }

  function start() {
    if (raf || !skeleton || paused || lost || disposed || failed) return;
    last = 0;
    lastDraw = 0;
    raf = requestAnimationFrame(frame);
  }

  function stop() {
    if (raf) cancelAnimationFrame(raf);
    raf = 0;
  }

  function keepClipsEnding() {
    var ClippingAttachment = spine.ClippingAttachment;
    var RegionAttachment = spine.RegionAttachment;
    var MeshAttachment = spine.MeshAttachment;
    var slots = skeleton.slots;
    var i;
    for (i = 0; i < slots.length; i++) {
      var slot = slots[i];
      var attachment = slot.getAttachment();
      if (attachment instanceof ClippingAttachment && attachment.endSlot && !clips.has(attachment)) {
        clips.set(attachment, {
          attachment: attachment,
          slot: slot,
          end: attachment.endSlot,
          detached: false,
        });
      }
    }
    var order = skeleton.drawOrder;
    clips.forEach(function (clip) {
      var current = clip.slot.getAttachment();
      if (current !== clip.attachment && !(clip.detached && current === null)) {
        clip.detached = false;
        return;
      }
      var from = order.indexOf(clip.slot);
      var to = -1;
      for (var index = 0; index < order.length; index++) {
        if (order[index].data === clip.end) {
          to = index;
          break;
        }
      }
      if (from < 0 || to < 0) return;
      var lastSlot = null;
      for (var j = to; j > from; j--) {
        var drawn = order[j].getAttachment();
        if (drawn instanceof RegionAttachment || drawn instanceof MeshAttachment) {
          lastSlot = order[j].data;
          break;
        }
      }
      if (lastSlot) {
        clip.attachment.endSlot = lastSlot;
        if (clip.detached) clip.slot.setAttachment(clip.attachment);
        clip.detached = false;
      } else {
        clip.slot.setAttachment(null);
        clip.detached = true;
      }
    });
  }

  function aim(aspect) {
    var camera = renderer.camera;
    var viewHeight = Math.max(
      bounds.height * FRAMING_HEIGHT,
      (bounds.width * MIN_WIDTH) / aspect
    );
    var top = bounds.y + bounds.height * (1 + HEADROOM);
    camera.position.x = bounds.x + bounds.width / 2;
    camera.position.y = top - viewHeight / 2;
    camera.setViewport(viewHeight * aspect, viewHeight);
  }

  function draw(step) {
    var ratio = Math.min(window.devicePixelRatio || 1, MAX_PIXEL_RATIO);
    // Android WebView 里 100% 高度偶尔会算成 0；用窗口尺寸兜底。
    if (!canvas.clientWidth || !canvas.clientHeight) {
      canvas.style.width =
        (window.innerWidth || document.documentElement.clientWidth || 1) + "px";
      canvas.style.height =
        (window.innerHeight || document.documentElement.clientHeight || 1) + "px";
    }
    var width = Math.max(1, Math.round(canvas.clientWidth * ratio));
    var height = Math.max(1, Math.round(canvas.clientHeight * ratio));
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    gl.viewport(0, 0, width, height);
    gl.clearColor(0, 0, 0, 0);
    gl.clear(gl.COLOR_BUFFER_BIT);

    state.update(step);
    state.apply(skeleton);
    skeleton.updateWorldTransform();
    keepClipsEnding();
    aim(width / height);
    renderer.begin();
    renderer.drawSkeleton(skeleton, true);
    renderer.end();
  }

  function frame(now) {
    raf = requestAnimationFrame(frame);
    // 最多 60 帧；WebView 在省电或后台时不必白白重绘。
    if (now - lastDraw < 1000 / 60) return;
    var step = last ? Math.min((now - last) / 1000, MAX_STEP_SECONDS) : 0;
    last = now;
    lastDraw = now;
    draw(step);
  }

  function boot() {
    spine = window.spine;
    if (!spine) {
      notifyFailed("Spine runtime 没有加载");
      return;
    }
    try {
      var context = new spine.webgl.ManagedWebGLRenderingContext(canvas, {
        alpha: true,
        premultipliedAlpha: true,
      });
      if (!context.gl) {
        notifyFailed("这台设备不支持 WebGL");
        return;
      }
      gl = context.gl;
      lost = gl.isContextLost();
      gl.pixelStorei(UNPACK_PREMULTIPLY_ALPHA_WEBGL, true);
      renderer = new spine.webgl.SceneRenderer(canvas, context, true);
      assets = new spine.webgl.AssetManager(context);

      canvas.addEventListener("webglcontextlost", function () {
        lost = true;
        stop();
      }, false);
      canvas.addEventListener("webglcontextrestored", function () {
        gl.pixelStorei(UNPACK_PREMULTIPLY_ALPHA_WEBGL, true);
        lost = false;
        start();
      }, false);
    } catch (e) {
      notifyFailed(e && e.message ? e.message : "无法创建立绘画布");
      return;
    }

    var outfit = null;
    try {
      outfit = new URLSearchParams(window.location.search).get("outfit");
    } catch (e) {
      outfit = null;
    }
    if (!outfit) {
      notifyFailed("没有立绘地址");
      return;
    }
    load(outfit);
  }

  // The Compose side calls these once the page has loaded (and on every state change).
  window.stand = {
    setPaused: function (value) {
      paused = !!value;
      if (paused) stop();
      else start();
    },
    setDirection: function (value) {
      if (!value) return;
      direction = {
        mood: value.mood || "calm",
        speaking: !!value.speaking,
        looking: !!value.looking,
        nod: value.nod || null,
      };
      follow();
    },
  };

  window.addEventListener("error", function (event) {
    if (event && event.message) notifyFailed(event.message);
  }, false);

  boot();
})();
