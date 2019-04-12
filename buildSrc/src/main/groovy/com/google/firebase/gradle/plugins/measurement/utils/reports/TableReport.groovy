// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.gradle.plugins.measurement.utils.reports

import com.google.firebase.gradle.plugins.measurement.utils.enums.ColumnName
import com.google.firebase.gradle.plugins.measurement.utils.enums.TableName
import de.vandermeer.asciitable.AsciiTable
import de.vandermeer.asciitable.CWC_LongestLine
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment

/**
 * Holds tabular data and can be serialized as a human readable text table.
 *
 * <p>For example:
 * ┌────────────────────────────────────────────────────────┐
 * │                     Line Coverages                     │
 * ├─────────────────────────────────┬──────────────────────┤
 * │ project                         │ coverage percent     │
 * ├─────────────────────────────────┼──────────────────────┤
 * │ firebase-common                 │ 0.7061310782241015   │
 * │ firebase-common-ktx             │ 0.9090909090909091   │
 * │ firebase-database               │ 0.509516668589226    │
 * │ firebase-database-collection    │ 0.7685185185185185   │
 * │ firebase-datatransport          │ 1.0                  │
 * │ firebase-firestore              │ 0.4179763137749358   │
 * │ firebase-firestore-ktx          │ 0.2857142857142857   │
 * │ firebase-functions              │ 0.038461538461538464 │
 * │ firebase-inappmessaging-display │ 0.28852056476365867  │
 * │ firebase-storage                │ 0.8417399352151782   │
 * └─────────────────────────────────┴──────────────────────┘
 */
class TableReport {
    TableName tableName
    List<ColumnName> columnNames
    List<List<Object>> replaceMeasurements

    TableReport(TableName tableName = null, List<ColumnName> columnNames = [],
                List<List<Object>> replaceMeasurements = []) {
        this.tableName = tableName
        this.columnNames = columnNames
        this.replaceMeasurements = replaceMeasurements
    }

    static TableReport fromTable(Table table) {
        return new TableReport(tableName: table.tableName,
                columnNames: table.columnNames,
                replaceMeasurements: table.replaceMeasurements)
    }

    void addReplaceMeasurement(List<Object> replaceMeasurement) {
        assert columnNames.size() == replaceMeasurement.size():
                'Count of columns and replacement values should match.'
        this.replaceMeasurements.add(replaceMeasurement)
    }

    @Override
    String toString() {
        assert tableName: 'Table name should not be null.'
        assert columnNames.size(): 'Should have at least one column.'
        replaceMeasurements.each {
            assert it.size() == columnNames.size():
                    "Columns '$columnNames' and row values '$it' should be of same length."
        }

        def table = new AsciiTable()
        table.addRule()

        // Set the title.
        // Span title cell by setting the value of all previous cells to null.
        // ┌───────────────────────────────────┐
        // │               title               │
        // ├───────────┬───────────┬───────────┤
        // │ column1   │ column2   │ ......    │
        // ├───────────┼───────────┼───────────┤
        def titleRow = columnNames.size() < 2 ? [tableName] :
                [*(2..columnNames.size()).collect { null }, tableName]
        def title = table.addRow(titleRow)
        title.setTextAlignment(TextAlignment.CENTER)
        table.addRule()

        // Set column names.
        table.addRow(columnNames)
        table.addRule()

        // Set replace measurements.
        for (measurement in replaceMeasurements) {
            table.addRow(measurement*.toString())
        }
        table.addRule()

        // Set formatting options.
        table.getRenderer().setCWC(new CWC_LongestLine())
        table.setPaddingLeftRight(1, 1)

        return table.render()
    }

}
