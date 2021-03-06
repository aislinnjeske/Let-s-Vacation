import scala.util.{Try, Success, Failure}
import org.apache.spark.sql.SparkSession
import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import java.lang.Double
import org.apache.spark.sql.functions._

object readAirbnbData {

def main(args: Array[String]){
    
    //Create the spark session and the spark context
    val spark = SparkSession.builder.appName("Lets-Vacation").getOrCreate()
    val sc = SparkContext.getOrCreate()
    val sqlContext = new SQLContext(sc)
    import spark.implicits._
    import sqlContext.implicits._
    
    //Read in the data from HDFS csv file parsing on ;
    val inputData = sqlContext.read.format("csv").option("header", "true").option("delimiter", ";").load("hdfs://carson-city:8624/termProject/cityData/*.csv")
    
    //Append the file name to the dataframe to group the housing details
    val inputDataWithFileName = inputData.withColumn("filename", input_file_name())
    
    //Select only the columns we need and drop any columns with null 
    val selectedDataPrice = inputDataWithFileName.select("filename", "country", "room_type", "price").na.drop()
    
    val newRoomTypes = selectedDataPrice.withColumn("room_type", when(selectedDataPrice("room_type") === "Entire home/apt", "hi"). when(selectedDataPrice("room_type") === "Shared room", "lo").when(selectedDataPrice("room_type") === "Private room", "mid").otherwise(selectedDataPrice("room_type")))

    //filter out columns where the country is not a word and where the price is in $x.xx format
    val noNumberDataPrice = newRoomTypes.filter(newRoomTypes("country") rlike "^[a-zA-Z ]{2,}$")
    val numberedPrice = noNumberDataPrice.filter(noNumberDataPrice("price") rlike "^\\$[0-9]+\\.[0-9]+$")
    
    //Convert the dataframe to an rdd
    val rowsPrice : RDD[Row] = numberedPrice.rdd
    
    //Create key value pairs with key being City, Country, Room-Type and value is the housing price
    val keyValuePairsPrice = rowsPrice.map(s => ( s.get(0).toString.substring(s.get(0).toString.lastIndexOf('/') + 1, s.get(0).toString.indexOf('.')).capitalize.replace("-", " ") + "," + s.get(2), Double.parseDouble(s.get(3).toString.substring(1).replace(",",""))))
    
    //Calculate average values for each key
    val meansPrice = keyValuePairsPrice.groupByKey.mapValues(x => x.sum/x.size)

    //Write the means to text file
    //means.saveAsTextFile("hdfs://carson-city:8624/termProject/airbnbData/output3")
    
    //Write the means to stdout in spark
    meansPrice.take(10000).foreach(println)
    }

}
