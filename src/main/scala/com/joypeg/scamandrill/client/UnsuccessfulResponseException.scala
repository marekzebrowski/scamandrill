package com.joypeg.scamandrill.client

class UnsuccessfulResponseException(val status: Int,val reason: String, val msg: String) extends RuntimeException{
}
