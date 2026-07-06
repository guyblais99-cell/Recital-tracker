/**
 * Main-thread AprilTag 36h11 detector (wraps apriltag_wasm.js).
 * Note: ApriltagDetector is also inlined in index.html for deployments without lib/.
 */
class ApriltagDetector {
    constructor() {
        this._Module = null;
        this._ready = false;
        this._initPromise = null;
    }

    static wasmBaseUrl() {
        const scripts = document.getElementsByTagName('script');
        for (let i = scripts.length - 1; i >= 0; i--) {
            const src = scripts[i].src;
            if (src && src.indexOf('apriltag_wasm.js') !== -1) {
                return src.slice(0, src.lastIndexOf('/') + 1);
            }
        }
        return window.APRILTAG_WASM_CDN || new URL('lib/', window.location.href).href;
    }

    init() {
        if (this._initPromise) return this._initPromise;
        const ready = window.__apriltagWasmReady || Promise.resolve();
        this._initPromise = ready.then(() => {
            if (typeof AprilTagWasm !== 'function') {
                throw new Error('AprilTagWasm not available after script load');
            }
            const wasmBase = ApriltagDetector.wasmBaseUrl();
            return AprilTagWasm({
                locateFile: (path) => wasmBase + path
            });
        }).then((Module) => {
            this._Module = Module;
            this._atagInit = Module.cwrap('atagjs_init', 'number', []);
            this._destroy = Module.cwrap('atagjs_destroy', 'number', []);
            this._set_detector_options = Module.cwrap('atagjs_set_detector_options', 'number',
                ['number', 'number', 'number', 'number', 'number', 'number', 'number']);
            this._set_img_buffer = Module.cwrap('atagjs_set_img_buffer', 'number', ['number', 'number', 'number']);
            this._detect = Module.cwrap('atagjs_detect', 'number', []);
            this._atagInit();
            this._set_detector_options(1.5, 0.0, 1, 1, 0, 0, 0);
            this._ready = true;
            return this;
        });
        return this._initPromise;
    }

    isReady() {
        return this._ready;
    }

    detectGrayscale(gray, width, height) {
        if (!this._ready) return [];
        const imgBuffer = this._set_img_buffer(width, height, width);
        if (width * height > gray.length) return [];
        this._Module.HEAPU8.set(gray, imgBuffer);
        const strJsonPtr = this._detect();
        const strJsonLen = this._Module.getValue(strJsonPtr, 'i32');
        if (!strJsonLen) return [];
        const strJsonStrPtr = this._Module.getValue(strJsonPtr + 4, 'i32');
        const view = new Uint8Array(this._Module.HEAP8.buffer, strJsonStrPtr, strJsonLen);
        let json = '';
        for (let i = 0; i < strJsonLen; i++) json += String.fromCharCode(view[i]);
        try {
            const parsed = JSON.parse(json);
            return Array.isArray(parsed) ? parsed : [];
        } catch (_) {
            return [];
        }
    }
}

window.ApriltagDetector = ApriltagDetector;
