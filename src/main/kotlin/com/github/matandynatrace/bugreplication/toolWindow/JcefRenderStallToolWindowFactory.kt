package com.github.matandynatrace.bugreplication.toolWindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.awt.BorderLayout
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.Timer
import javax.swing.border.EmptyBorder

/**
 * Reproduces the JCEF rendering stall, mimicking the real snapshot pane flow:
 *
 * 1. User clicks a snapshot row → webview sends cefQuery "messageSelected:snap-X"
 * 2. Java immediately sends progress.show → React renders loading spinner
 * 3. After 800ms Java sends message.enrich.success → React re-renders detail panel
 * 4. BUG: the React re-render completes in JS memory but JCEF never visually repaints
 *    the Swing component — right panel stays frozen (loading or black) until the user
 *    resizes the window or moves the mouse over it, which incidentally triggers a repaint
 *
 * Toggle the fix to confirm cefBrowser.invalidate() + component.repaint() resolves the stall.
 */
class JcefRenderStallToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val log = thisLogger()
        val browser = JBCefBrowser()
        val fixEnabled = AtomicBoolean(false)
        val executor = Executors.newSingleThreadScheduledExecutor()

        browser.loadHTML(buildHtml())

        val msgRouter = CefMessageRouter.create()
        msgRouter.addHandler(object : CefMessageRouterHandlerAdapter() {
            override fun onQuery(
                cefBrowser: CefBrowser?,
                frame: CefFrame?,
                queryId: Long,
                request: String?,
                persistent: Boolean,
                callback: CefQueryCallback?
            ): Boolean {
                if (request?.startsWith("messageSelected:") != true) return false
                val id = request.substringAfter("messageSelected:")
                log.info("[RENDER-STALL] 1/6 query received id=$id fix=${fixEnabled.get()} ts=${System.currentTimeMillis()}")

                // Step 1: immediately show loading state — same as real plugin sending progress.show
                sendToWebView(browser, fixEnabled, "progress.show", """{"messageType":"progress.show","body":null}""")

                // Step 2: after simulated server round-trip, deliver snapshot details
                log.info("[RENDER-STALL] 1/6 scheduling message.enrich.success id=$id delay=800ms")
                executor.schedule({
                    log.info("[RENDER-STALL] 1/6 message.enrich.success firing id=$id ts=${System.currentTimeMillis()}")
                    sendToWebView(
                        browser, fixEnabled, "message.enrich.success",
                        """{"messageType":"message.enrich.success","body":${buildDetails(id)}}"""
                    )
                }, 800L, TimeUnit.MILLISECONDS)

                callback?.success("ok")
                return true
            }
        }, false)
        browser.cefBrowser.client.addMessageRouter(msgRouter)

        Disposer.register(project) {
            executor.shutdownNow()
            browser.cefBrowser.client.removeMessageRouter(msgRouter)
            msgRouter.dispose()
        }

        val fixButton = JButton("Enable Fix (invalidate + repaint)").apply {
            addActionListener {
                val next = !fixEnabled.get()
                fixEnabled.set(next)
                text = if (next) "Disable Fix" else "Enable Fix (invalidate + repaint)"
            }
        }

        val statusLabel = JLabel()
        Timer(300) {
            statusLabel.text = if (fixEnabled.get())
                "Fix: ON — detail panel renders immediately after ~800ms"
            else
                "Fix: OFF — click a snapshot; right panel freezes after loading (resize/hover to force repaint)"
        }.also { it.isRepeats = true; it.start() }

        val devToolsButton = JButton("Open DevTools").apply {
            addActionListener { browser.openDevtools() }
        }

        val controlPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = EmptyBorder(6, 8, 6, 8)
            add(fixButton)
            add(Box.createHorizontalStrut(12))
            add(devToolsButton)
            add(Box.createHorizontalStrut(12))
            add(statusLabel)
        }

        val root = JPanel(BorderLayout()).apply {
            add(controlPanel, BorderLayout.NORTH)
            add(browser.component, BorderLayout.CENTER)
        }

        val content = ContentFactory.getInstance().createContent(root, "Render Stall", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun sendToWebView(browser: JBCefBrowser, fixEnabled: AtomicBoolean, msgType: String, json: String) {
        thisLogger().info("[RENDER-STALL] 2/6 invokeLater queued msgType=$msgType ts=${System.currentTimeMillis()}")
        ApplicationManager.getApplication().invokeLater {
            thisLogger().info("[RENDER-STALL] 2/6 executeJavaScript calling msgType=$msgType ts=${System.currentTimeMillis()}")
            browser.cefBrowser.executeJavaScript("window.receiveMessage($json)", "", 0)
            thisLogger().info("[RENDER-STALL] 2/6 executeJavaScript returned msgType=$msgType ts=${System.currentTimeMillis()}")
            if (fixEnabled.get()) {
                browser.cefBrowser.invalidate()
                browser.component.repaint()
            }
        }
    }

    private fun buildDetails(id: String): String {
        val index = id.removePrefix("snap-").toIntOrNull() ?: 1
        return """{"id":"$id","breakpoint":"TodoController.java : ${18 + index}","variables":[{"name":"this","value":"TodoController { id=${1000 + index} }"},{"name":"todos","value":"ArrayList(${10 + index} items)"},{"name":"request","value":"GET /api/todos"},{"name":"pageSize","value":"${index * 10}"}],"stackTrace":["TodoController.getTodos(TodoController.java:${18 + index})","InvocableHandlerMethod.invoke(InvocableHandlerMethod.java:205)","RequestMappingHandlerAdapter.handle(RequestMappingHandlerAdapter.java:920)"]}"""
    }

    private fun buildHtml(): String {
        // React 18 UMD (no JSX, no Babel needed — React.createElement directly)
        // CDN scripts load fine in JBCefBrowser; the page uses absolute URLs.
        return """
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <script src="https://unpkg.com/react@18/umd/react.development.js"></script>
  <script src="https://unpkg.com/react-dom@18/umd/react-dom.development.js"></script>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: 'JetBrains Mono', monospace, sans-serif;
      background: #1e1e1e; color: #d4d4d4;
      height: 100vh; overflow: hidden; font-size: 13px;
    }
    .app { display: flex; height: 100vh; }

    /* Left panel */
    .snapshot-list { width: 55%; border-right: 1px solid #3e3e3e; display: flex; flex-direction: column; overflow: hidden; }
    .list-header { padding: 6px 12px; background: #252526; color: #888; font-size: 11px; text-transform: uppercase; letter-spacing: 1px; border-bottom: 1px solid #3e3e3e; }
    .snapshot-rows { overflow-y: auto; flex: 1; }
    .snapshot-row { display: grid; grid-template-columns: 1fr 1fr auto; gap: 8px; padding: 7px 12px; cursor: pointer; border-bottom: 1px solid #2a2a2a; }
    .snapshot-row:hover { background: #2a2d2e; }
    .snapshot-row.selected { background: #094771; }
    .snap-label { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .snap-bp { color: #888; white-space: nowrap; }
    .snap-ts { color: #888; font-size: 11px; text-align: right; white-space: nowrap; }

    /* Right panel */
    .detail-panel { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
    .detail-idle { display: flex; align-items: center; justify-content: center; height: 100%; color: #555; font-size: 12px; }
    .detail-loading { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; gap: 16px; color: #999; }
    .spinner { width: 24px; height: 24px; border: 2px solid #444; border-top-color: #4fc3f7; border-radius: 50%; animation: spin 1s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }
    .tabs { display: flex; border-bottom: 1px solid #3e3e3e; padding: 0 12px; }
    .tab { padding: 8px 16px; color: #888; font-size: 11px; letter-spacing: 0.5px; border-bottom: 2px solid transparent; }
    .tab.active { color: #4fc3f7; border-bottom-color: #4fc3f7; }
    .variables { padding: 12px; overflow-y: auto; flex: 1; }
    .var-row { display: flex; gap: 12px; padding: 4px 0; border-bottom: 1px solid #2a2a2a; }
    .var-name { color: #9cdcfe; min-width: 120px; }
    .var-value { color: #ce9178; }
    .stack-trace { padding: 12px; font-size: 11px; border-top: 1px solid #3e3e3e; }
    .stack-frame { padding: 3px 0; color: #888; }
  </style>
</head>
<body>
  <div id="root"></div>
  <script>
    'use strict';
    const e = React.createElement;
    const { useState, useEffect } = React;

    // ── Minimal Zustand-like store ────────────────────────────────────────────────
    function createStore(init) {
      let state;
      const listeners = new Set();
      const set = function(partial) {
        const next = typeof partial === 'function' ? partial(state) : partial;
        state = Object.assign({}, state, next);
        listeners.forEach(function(l) { l(state); });
      };
      state = init(set);
      return {
        getState: function() { return state; },
        subscribe: function(l) { listeners.add(l); return function() { listeners.delete(l); }; },
      };
    }

    function useStore(store, selector) {
      const sel = selector || function(s) { return s; };
      const snap = useState(function() { return sel(store.getState()); });
      const val = snap[0], setVal = snap[1];
      useEffect(function() { return store.subscribe(function(s) { setVal(sel(s)); }); }, []);
      return val;
    }

    // ── Snapshot store (mirrors snapshotsView appState + infoScreenStore) ────────
    const snapshotStore = createStore(function(set) {
      return {
        status: 'idle',      // 'idle' | 'loading' | 'loaded'
        selectedId: null,
        details: null,
        showLoading: function() {
          console.log('[RENDER-STALL] 4/6 store.showLoading ts:', Date.now());
          set({ status: 'loading', details: null });
        },
        showDetails: function(d) {
          console.log('[RENDER-STALL] 4/6 store.showDetails id:', d.id, 'ts:', Date.now());
          set({ status: 'loaded', details: d });
        },
        select: function(id) { set({ selectedId: id }); },
      };
    });

    // ── Java → webview bridge (mirrors window.receiveMessage in the real app) ────
    window.receiveMessage = function(msg) {
      console.log('[RENDER-STALL] 3/6 receiveMessage', msg.messageType, 'ts:', Date.now());
      document.title = msg.messageType + ':' + Date.now();
      const s = snapshotStore.getState();
      if (msg.messageType === 'progress.show') {
        s.showLoading();
      } else if (msg.messageType === 'message.enrich.success') {
        s.showDetails(msg.body);
      }
    };

    // ── Fake snapshot data ────────────────────────────────────────────────────────
    const SNAPSHOTS = [];
    for (var i = 0; i < 10; i++) {
      SNAPSHOTS.push({
        id: 'snap-' + (i + 1),
        label: 'Hit on TodoController.java:' + (19 + i),
        breakpoint: 'TodoController.java : ' + (19 + i),
        timestamp: '11:5' + (i % 10) + ':' + (i < 10 ? '0' : '') + i + ':396 AM',
      });
    }

    // ── Components ────────────────────────────────────────────────────────────────
    function SnapshotRow(props) {
      const cls = 'snapshot-row' + (props.isSelected ? ' selected' : '');
      return e('div', { className: cls, onClick: function() { props.onSelect(props.snapshot.id); } },
        e('span', { className: 'snap-label' }, props.snapshot.label),
        e('span', { className: 'snap-bp' }, props.snapshot.breakpoint),
        e('span', { className: 'snap-ts' }, props.snapshot.timestamp)
      );
    }

    function SnapshotList() {
      const selectedId = useStore(snapshotStore, function(s) { return s.selectedId; });

      function handleSelect(id) {
        snapshotStore.getState().select(id);
        if (window.cefQuery) {
          window.cefQuery({ request: 'messageSelected:' + id, onSuccess: function() {}, onFailure: function() {} });
        }
      }

      return e('div', { className: 'snapshot-list' },
        e('div', { className: 'list-header' }, '(10) SNAPSHOTS'),
        e('div', { className: 'snapshot-rows' },
          SNAPSHOTS.map(function(s) {
            return e(SnapshotRow, { key: s.id, snapshot: s, isSelected: selectedId === s.id, onSelect: handleSelect });
          })
        )
      );
    }

    function DetailPanel() {
      const state = useStore(snapshotStore, function(s) { return { status: s.status, details: s.details }; });
      const status = state.status, details = state.details;

      console.log('[RENDER-STALL] 5/6 DetailPanel render status:', status, 'ts:', Date.now());

      useEffect(function() {
        console.log('[RENDER-STALL] 6/6 DOM committed status:', status, details ? 'id:' + details.id : '', 'ts:', Date.now());
      }, [status]);

      if (status === 'idle') {
        return e('div', { className: 'detail-idle' }, '← Select a snapshot to view details');
      }

      if (status === 'loading') {
        return e('div', { className: 'detail-loading' },
          e('div', { className: 'spinner' }),
          e('span', null, 'Loading...')
        );
      }

      // status === 'loaded'
      return e('div', { className: 'detail-panel' },
        e('div', { className: 'tabs' },
          e('span', { className: 'tab active' }, 'VARIABLES'),
          e('span', { className: 'tab' }, 'PROCESS'),
          e('span', { className: 'tab' }, 'STACK TRACE'),
          e('span', { className: 'tab' }, 'TRACING')
        ),
        e('div', { className: 'variables' },
          details.variables.map(function(v, idx) {
            return e('div', { key: idx, className: 'var-row' },
              e('span', { className: 'var-name' }, v.name),
              e('span', { className: 'var-value' }, '= ' + v.value)
            );
          })
        ),
        e('div', { className: 'stack-trace' },
          details.stackTrace.map(function(f, idx) {
            return e('div', { key: idx, className: 'stack-frame' }, f);
          })
        )
      );
    }

    function App() {
      return e('div', { className: 'app' },
        e(SnapshotList, null),
        e(DetailPanel, null)
      );
    }

    ReactDOM.createRoot(document.getElementById('root')).render(e(App, null));
  </script>
</body>
</html>
        """.trimIndent()
    }
}
