package com.github.matandynatrace.bugreplication.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.jcef.JBCefBrowser
 import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.content.ContentFactory
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.handler.CefMessageRouterHandlerAdapter
import javax.swing.JPanel
import java.awt.BorderLayout

class JcefToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel()
        panel.layout = javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS)

        repeat(3) { idx ->
            val browser = JBCefBrowser()
            browser.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, true)

            val msgHandler = object : CefMessageRouterHandlerAdapter() {
                override fun onQuery(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    queryId: Long,
                    request: String?,
                    persistent: Boolean,
                    callback: org.cef.callback.CefQueryCallback?
                ): Boolean {
                    try {
                        callback?.success("ToolWindow #${idx + 1} Response: $request")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback?.failure(500, "Error: ${e.message}")
                    }
                    return true
                }
            }
            val msgRouter = CefMessageRouter.create(msgHandler)
            browser.cefBrowser.client.addMessageRouter(msgRouter)

            val html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>JCEF Tool Window #${idx + 1}</title>
                    <style>
                        /* Simulate large CSS bundle */
                        body { font-family: Arial, sans-serif; padding: 20px; background: #f5f5f5; }
                        .container { max-width: 600px; margin: 0 auto; }
                        button { padding: 12px 24px; margin: 8px; background: #007acc; color: white; border: none; border-radius: 4px; cursor: pointer; }
                        button:hover { background: #005a99; }
                        #result { margin-top: 20px; padding: 10px; background: white; border-radius: 4px; border: 1px solid #ddd; }
                        #cefQueryStatus { color: red; font-weight: bold; margin-bottom: 10px; }
                        /* Large dummy CSS */
                        ${".".repeat(10000)}
                    </style>
                    <!-- Simulate large vendor script (e.g., React, lodash) -->
                    <script>
                        // Minified React-like dummy code (not functional, just for size)
                        ${"var React=function(){};".repeat(10000)}
                        // Minified lodash-like dummy code
                        ${"var _=function(){};".repeat(10000)}
                    </script>
                </head>
                <body>
                    <div class="container">
                        <h1>JCEF Tool Window #${idx + 1} Message Router Test</h1>
                        <div id="cefQueryStatus"></div>
                        <button onclick="testQuery('toolwindow_message_${idx + 1}')">Send Test Query</button>
                        <div id="result">Click the button to test the message router...</div>
                        <!-- Simulate large DOM tree -->
                        ${"<div class='dummy'>" + "x".repeat(10000) + "</div>".repeat(50)}
                    </div>
                    <script>
                        // Simulate large inline app code
                        ${"function dummyAppFn(){};".repeat(10000)}
                        function testQuery(message) {
                            const result = document.getElementById('result');
                            if (!window.cefQuery) {
                                result.innerHTML = '<strong>Error:</strong> window.cefQuery is not available!';
                                return;
                            }
                            result.innerHTML = 'Sending query: ' + message + '...';
                            window.cefQuery({
                                request: message,
                                onSuccess: function(response) {
                                    result.innerHTML = '<strong>Success:</strong> ' + response;
                                },
                                onFailure: function(error_code, error_message) {
                                    result.innerHTML = '<strong>Error:</strong> ' + error_message + ' (Code: ' + error_code + ')';
                                }
                            });
                        }
                        window.addEventListener('DOMContentLoaded', function() {
                            var status = document.getElementById('cefQueryStatus');
                            if (window.cefQuery) {
                                status.innerHTML = 'window.cefQuery is available.';
                                status.style.color = 'green';
                            } else {
                                status.innerHTML = 'window.cefQuery is NOT available!';
                                status.style.color = 'red';
                            }
                        });
                    </script>
                </body>
                </html>
            """
            browser.loadHTML(html)
            val browserPanel = JPanel(BorderLayout())
            browserPanel.add(browser.component, BorderLayout.CENTER)
            browserPanel.border = javax.swing.BorderFactory.createTitledBorder("JCEF Browser #${idx + 1}")
            panel.add(browserPanel)
        }

        val content = ContentFactory.getInstance().createContent(panel, "JCEF HTML (3 Browsers)", false)
        toolWindow.contentManager.addContent(content)
    }
}
