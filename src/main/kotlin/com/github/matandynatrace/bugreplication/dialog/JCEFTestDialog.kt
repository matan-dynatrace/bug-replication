package com.github.matandynatrace.bugreplication.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.BorderLayout

/**
 * Custom JCEF WebView that mimics the real implementation pattern
 * This reproduces the exact pattern that causes the NullPointerException
 */
class CustomJCEFWebView() : JPanel(), AutoCloseable {

    private var browser: JBCefBrowser? = null
    private var msgRouter: CefMessageRouter? = null
    private var msgHandler: CefMessageRouterHandlerAdapter? = null
    private var isDisposed = false

    init {
        layout = BorderLayout()
        initializeBrowser()
    }

    private fun initializeBrowser() {
        try {
            // Create browser instance
            browser = JBCefBrowser()
            browser?.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, true)

            // Create message router handler - this mimics the real implementation
            msgHandler = object : CefMessageRouterHandlerAdapter() {
                override fun onQuery(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    queryId: Long,
                    request: String?,
                    persistent: Boolean,
                    callback: org.cef.callback.CefQueryCallback?
                ): Boolean {
                    // Simple test response
                    try {
                        callback?.success("Response: $request")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback?.failure(500, "Error: ${e.message}")
                    }
                    return true
                }
            }

            // Register message router - this is the exact pattern from the real code
            registerMessageHandler()

            // Load test HTML with JavaScript communication
            val htmlContent = createTestHTML()
            browser?.loadHTML(htmlContent)

            // Add to panel
            browser?.component?.let { add(it, BorderLayout.CENTER) }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun registerMessageHandler() {
        try {
            val client = browser?.cefBrowser?.client
            // This is the exact line from the real implementation that causes the bug
            msgRouter = CefMessageRouter.create(msgHandler)
            client?.addMessageRouter(msgRouter)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createTestHTML(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>JCEF Bug Reproduction</title>
                <style>
                    body { font-family: Arial, sans-serif; padding: 20px; background: #f5f5f5; }
                    .container { max-width: 600px; margin: 0 auto; }
                    button { 
                        padding: 12px 24px; 
                        margin: 8px; 
                        background: #007acc; 
                        color: white; 
                        border: none; 
                        border-radius: 4px; 
                        cursor: pointer;
                    }
                    button:hover { background: #005a99; }
                    #result { 
                        margin-top: 20px; 
                        padding: 10px; 
                        background: white; 
                        border-radius: 4px; 
                        border: 1px solid #ddd;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>JCEF Message Router Bug Test</h1>
                    <p>This page tests the JCEF message router that triggers the NullPointerException.</p>
                    <p><strong>Instructions:</strong> Click the button below, then close and reopen this dialog 2-3 times rapidly.</p>
                    
                    <button onclick="testQuery('test_message_1')">Test Message 1</button>
                    <button onclick="testQuery('test_message_2')">Test Message 2</button>
                    <button onclick="rapidTest()">Rapid Test (Multiple Queries)</button>
                    
                    <div id="result">Click a button to test the message router...</div>
                </div>
                
                <script>
                    let queryCount = 0;
                    
                    function testQuery(message) {
                        queryCount++;
                        const result = document.getElementById('result');
                        result.innerHTML = 'Sending query ' + queryCount + ': ' + message + '...';
                        
                        window.cefQuery({
                            request: message + '_' + queryCount,
                            onSuccess: function(response) {
                                result.innerHTML = '<strong>Success:</strong> ' + response;
                            },
                            onFailure: function(error_code, error_message) {
                                result.innerHTML = '<strong>Error:</strong> ' + error_message + ' (Code: ' + error_code + ')';
                            }
                        });
                    }
                    
                    function rapidTest() {
                        // Send multiple rapid queries to stress test the message router
                        for (let i = 0; i < 5; i++) {
                            setTimeout(() => testQuery('rapid_test_' + i), i * 100);
                        }
                    }
                    
                    // Auto-test on load to trigger communication immediately
                    window.addEventListener('load', function() {
                        setTimeout(() => testQuery('auto_load_test'), 500);
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    override fun close() {
        dispose()
    }

    fun dispose() {
        try {
            if (!isDisposed) {
                isDisposed = true

                // This disposal pattern matches the real implementation
                // The bug likely occurs during this cleanup sequence
                val client = browser?.cefBrowser?.client
                if (msgRouter != null && client != null) {
                    try {
                        client.removeMessageRouter(msgRouter)
                    } catch (e: Exception) {
                        // This might be where the NullPointerException occurs
                        e.printStackTrace()
                    }
                }

                msgHandler = null
                msgRouter?.dispose()
                msgRouter = null
                browser?.dispose()
                browser = null
            }
        } catch (e: Exception) {
            // The bug manifests here during rapid open/close cycles
            e.printStackTrace()
        }
    }
}

class JCEFTestDialog(project: Project) : DialogWrapper(project) {

    private var customWebView: CustomJCEFWebView? = null

    init {
        title = "JCEF Bug Reproduction Dialog - Rapid Open/Close Test"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(900, 700)

        try {
            // Create the custom webview that mimics the real implementation
            customWebView = CustomJCEFWebView()
            panel.add(customWebView, BorderLayout.CENTER)

        } catch (e: Exception) {
            e.printStackTrace()
            val errorPanel = JPanel()
            errorPanel.add(javax.swing.JLabel("Error creating JCEF browser: ${e.message}"))
            panel.add(errorPanel, BorderLayout.CENTER)
        }

        return panel
    }

    override fun dispose() {
        try {
            // Dispose in the same order as the real implementation
            customWebView?.dispose()
            customWebView = null
        } catch (e: Exception) {
            // This is likely where the NullPointerException occurs during rapid cycles
            e.printStackTrace()
        } finally {
            super.dispose()
        }
    }

    override fun createActions(): Array<javax.swing.Action> {
        return arrayOf(okAction, cancelAction)
    }
}
