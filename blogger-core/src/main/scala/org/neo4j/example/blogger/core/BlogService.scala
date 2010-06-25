package org.neo4j.example.blogger.core

import model.{Blog, BlogUser}

trait BlogService
{
  def beginTx()

  def succeedTx()

  def failTx() 

  def shutdown:Unit

  def createUser(named:String, withPassword:String):Option[BlogUser]

  def userExists(named:String):Boolean
  
  def signIn(username:String, withPassword:String):Option[BlogUser]

  def findBlog(named:String):Option[Blog]

}

