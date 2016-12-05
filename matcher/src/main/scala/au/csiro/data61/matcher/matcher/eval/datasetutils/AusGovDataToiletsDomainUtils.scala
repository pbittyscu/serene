package au.csiro.data61.matcher.matcher.eval.datasetutils

import au.csiro.data61.matcher.data._
import au.csiro.data61.matcher.ingestion.loader._
import au.csiro.data61.matcher.matcher.eval._

import scala.util._

trait AusGovDataToiletsDomainUtils {
    val dataPath = "src/test/resources/datasets/open-gov-data/Toilets/AU"
    val gtPath = "src/test/resources/datasets/open-gov-data/Toilets/AU_labels.txt"

    def loadDataSets(): List[DataModel] = {
        val tableNameRegex = """^(.+).csv""".r
        val filesList = new java.io.File(dataPath).list.filter({
            case tableNameRegex(a) => true
            case _ => false
        }).map({
            case tableNameRegex(a) => a + ".csv"
        })

        filesList.map({case f => {
            CSVDataLoader("AU").loadTable(dataPath, f, "AU")
        }}).toList
    }

    def loadLabels(): Labels = {
        BasicLabels(CsvLabelsLoader().loadLabels(gtPath).toList)
    }

    def partitionDataSet(datasets: List[DataModel], labels: Labels, propTrain: Double, randNumGenerator: Random = new Random(1000)): ((List[DataModel],Labels), (List[DataModel],Labels)) = {
        DataPartitioner().partitionByAttribute(datasets, labels, propTrain)
    }
}