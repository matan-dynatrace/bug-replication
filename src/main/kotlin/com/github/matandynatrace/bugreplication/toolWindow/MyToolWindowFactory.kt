package com.github.matandynatrace.bugreplication.toolWindow

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.github.matandynatrace.bugreplication.MyBundle
import com.github.matandynatrace.bugreplication.dialog.JCEFTestDialog
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.BoxLayout
import javax.swing.border.EmptyBorder
import javax.swing.SwingUtilities

class MyToolWindowFactory : ToolWindowFactory {

    init {
        thisLogger().warn("JCEF Bug Reproduction Plugin - Testing NullPointerException issue with exact real-world pattern")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(private val toolWindow: ToolWindow) {

        fun getContent() = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            val mainPanel = JBPanel<JBPanel<*>>().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                border = EmptyBorder(10, 10, 10, 10)
            }

            val titleLabel = JBLabel("JCEF Bug Reproduction Tool").apply {
                font = font.deriveFont(16f).deriveFont(java.awt.Font.BOLD)
            }



            val jcefTestButton = JButton("Open JCEF Dialog (Bug Reproduction)").apply {
                addActionListener {
                    val dialog = JCEFTestDialog(toolWindow.project)
                    dialog.show()
                }
            }




            // Add components with spacing
            mainPanel.add(titleLabel)
            mainPanel.add(javax.swing.Box.createVerticalStrut(10))
            mainPanel.add(javax.swing.Box.createVerticalStrut(15))
            mainPanel.add(javax.swing.Box.createVerticalStrut(15))
            mainPanel.add(jcefTestButton)
            mainPanel.add(javax.swing.Box.createVerticalStrut(10))
            mainPanel.add(javax.swing.Box.createVerticalStrut(15))
            mainPanel.add(javax.swing.Box.createVerticalStrut(10))

            add(mainPanel, BorderLayout.CENTER)
        }
    }
}
