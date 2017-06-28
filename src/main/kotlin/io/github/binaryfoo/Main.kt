package io.github.binaryfoo

import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder
import com.amazonaws.services.cloudformation.model.*
import java.io.File

data class Dependency(val exportingStackName: String, val importingStackName: String, val exportName: String)

fun main(args: Array<String>) {
    val cfnClient = AmazonCloudFormationClientBuilder.defaultClient()
    val stackById = liveStacks(cfnClient)
    stackById.keys.forEach { println(it) }
    val dependencies = cfnClient.listExports(ListExportsRequest()).exports.flatMap { export ->
        listImports(cfnClient, export).map { import ->
            Dependency(stackById[export.exportingStackId]!!.stackName, import, export.name)
        }
    }

    val fileName = "graph"
    writeDot(fileName, dependencies)
    renderDot(fileName)
}

private fun liveStacks(cfnClient: AmazonCloudFormation): Map<String, StackSummary> {
    val statuses = StackStatus.values().filterNot { it.name.startsWith("DELETE") }.map { it.name }
    return cfnClient.listStacks(ListStacksRequest().withStackStatusFilters(statuses)).stackSummaries.map { it.stackId to it }.toMap()
}

private fun listImports(cfnClient: AmazonCloudFormation, export: Export): List<String> {
    try {
        return cfnClient.listImports(ListImportsRequest().withExportName(export.name)).imports
    } catch(e: Exception) {
        return emptyList()
    }
}

private fun writeDot(fileName: String, dependencies: List<Dependency>) {
    File("$fileName.dot").printWriter().use { w ->
        w.println("digraph StackExports {")
        w.println("  rankdir=LR")
        dependencies.forEach { (exporter, importer, name) ->
            w.println("  \"$importer\" -> \"$exporter\" [label=\"$name\"]")
        }
        w.println("}")
    }
}

private fun renderDot(fileName: String) {
    ProcessBuilder("/usr/local/bin/dot", "-T", "svg", "-o", "$fileName.svg", "$fileName.dot").inheritIO().start().waitFor()
}

