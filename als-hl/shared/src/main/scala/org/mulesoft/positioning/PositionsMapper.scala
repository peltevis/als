 package org.mulesoft.positioning

import org.mulesoft.typesystem.json.interfaces.{NodeRange, Point}
import org.mulesoft.typesystem.syaml.to.json.YPoint

import scala.collection.mutable.ArrayBuffer


class PositionsMapper(uri: String) extends  IPositionsMapper {

    var text: String = ""

    var lineLengths: ArrayBuffer[Int] = ArrayBuffer()

    var lineLengthSums: ArrayBuffer[Int] = ArrayBuffer() += 0

    def initRange(range:NodeRange):Unit = {
        initPoint(range.start)
        initPoint(range.end)
    }

    def initPoint(point:Point):Unit = {
        if(point.position >=0){
            return
        }
        point match {
            case yp:YPoint =>
                var pos = mapToPosition(point.line,point.column)
                yp.setPosition(pos)
            case _ =>
        }
    }

    def mapToPosition(line:Int,colum:Int):Int = {
        if(line<0||line>lineLengthSums.length-1){
            -1
        }
        else if(colum>lineLengths(line)){
            if(line == lineLengths.length-1){
                textLength
            }
            else {
                -1
            }
        }
        else{
            lineLengthSums(line) + colum
        }
    }

    def withText(_text: String): PositionsMapper = {
        setText(_text)
        this
    }


    def setText(_text: String): Unit = {
        text = _text
        initMapping()
    }

    def initMapping(): Unit = {

        var ind = 0
        val l = text.length

        var ignoreNext = false

        for {i <- 0 until l} {

            if (ignoreNext) {
                ignoreNext = false
            } else {

                ignoreNext = false

                if (text.charAt(i) == '\r') {
                    if (i < l - 1 && text.charAt(i + 1) == '\n') {

                        appendLineLength(i - ind + 2)

                        ind = i + 2

                        ignoreNext = true
                    } else {

                        appendLineLength(i - ind + 1)
                        ind = i + 1
                    }
                } else if (text.charAt(i) == '\n') {

                    appendLineLength(i - ind + 1)
                    ind = i + 1
                }
            }

        }

        appendLineLength(l - ind)
    }

    private def appendLineLength(value:Int): Unit ={
        lineLengths += value
        lineLengthSums += (lineLengthSums.last + value)
    }

    override def offset(position: Int): Int = {
        if(position<=0 || text.lengthCompare(position)<0){
            0
        }
        else{
            var ind = text.lastIndexOf('\n',position-1)
            if(ind<0){
                ind = 0
            }
            position - (ind+1)
        }
    }
    // $COVERAGE-OFF$
    override def lineOffset(str: String): Int = {
        var nonWhitespaceIndex = str.indexWhere(!Character.isWhitespace(_))
        if(nonWhitespaceIndex<0){
            nonWhitespaceIndex = str.length
        }
        val whiteSpaceLines = str.substring(0, nonWhitespaceIndex).split("\r\n").flatMap(_.split("\n"))
        if(whiteSpaceLines.isEmpty){
            0
        }
        else {
            var result = whiteSpaceLines.map(_.length).min
            result
        }
    }

    override def point(position: Int): Point = {

        if (position==text.length) {
            var line = lineLengths.length-1
            var column = lineLengths(lineLengths.length-1)
            YPoint(line, column, position)
        }
        else if (position == text.length + 1) {
            var line = lineLengths.length-1
            var column = lineLengths(lineLengths.length-1)-1
            YPoint(line, column, position-1)
        }
        else {
            var line = lineLengthSums.lastIndexWhere(position >= _)
            if(line < 0){
                var errorMessage = s"Character position exceeds text length: $position > ${text.length}. Path: $uri"
                throw new Error(errorMessage)
            }
            var column = if(line == 0) position else position - lineLengthSums(line)
            YPoint(line, column, position)
        }
    }

    override def lineString(line: Int): Option[String] = {
        if(line < 0 || line > lineLengthSums.length - 1){
            None
        }
        else {
            val start = lineLengthSums(line)
            val end = lineLengthSums(line + 1)
            val result = text.substring(start, end)
            Some(result)
        }
    }
    // $COVERAGE-ON$
    override def getText:String = text

    override def textLength: Int = text.length
    // $COVERAGE-OFF$
    override def line(lineIndex: Int): Option[String] = {
        if(lineIndex<0||lineIndex>lineLengthSums.length-1){
            None
        }
        else {
            var start = lineLengthSums(lineIndex)
            var end = lineLengthSums(lineIndex+1)
            Some(text.substring(start,end))
        }
    }

    override def lineContainingPosition(position: Int): Option[String] = {
        line(point(position).line)
    }
    // $COVERAGE-ON$
}

object PositionsMapper{
    def apply(uri: String):PositionsMapper = new PositionsMapper(uri)
}