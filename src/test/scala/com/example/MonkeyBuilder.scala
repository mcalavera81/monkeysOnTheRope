package com.example

import com.example.Rope.Side

import scala.collection.immutable.Stream
import scala.util.Random

/**
  * Created by mcalavera81 on 03/06/16.
  */
trait MonkeyBuilder{

  private val random = Random

  def createMonkey(side:Side)= (getCharStream.take(10).mkString ,side)

  def getCharStream: Stream[Char] = {
    def nextChar: Char = {
      val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
      chars charAt (random nextInt chars.length)
    }
    Stream continually nextChar
  }


}
