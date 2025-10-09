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
        val browser = JBCefBrowser()
        browser.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, true)

        // Create message router handler (like in the dialog)
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
                    callback?.success("ToolWindow Response: $request")
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
            <html lang=\"en\">
            <head>
                <meta charset=\"UTF-8\">
                <title>JCEF Tool Window</title>
                <style>
                    body { font-family: Arial, sans-serif; padding: 20px; background: #f5f5f5; }
                    .container { max-width: 600px; margin: 0 auto; }
                    button { padding: 12px 24px; margin: 8px; background: #007acc; color: white; border: none; border-radius: 4px; cursor: pointer; }
                    button:hover { background: #005a99; }
                    #result { margin-top: 20px; padding: 10px; background: white; border-radius: 4px; border: 1px solid #ddd; }
                    #cefQueryStatus { color: red; font-weight: bold; margin-bottom: 10px; }
                </style>
            </head>
            <body>
                <div class=\"container\">
                    <h1>JCEF Tool Window Message Router Test</h1>
                    <div id=\"cefQueryStatus\"></div>
                    <button onclick="testQuery('toolwindow_message')">Send Test Query</button>
                    <div id="result">Click the button to test the message router...</div>
                </div>
                <script>
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
                    // Show status of cefQuery injection
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

        val panel = JPanel(BorderLayout())
        panel.add(browser.component, BorderLayout.CENTER)
        val content = ContentFactory.getInstance().createContent(panel, "JCEF HTML", false)
        toolWindow.contentManager.addContent(content)
    }
}
