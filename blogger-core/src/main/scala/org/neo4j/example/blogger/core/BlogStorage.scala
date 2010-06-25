package org.neo4j.example.blogger.core

import model.{Blog, BlogUser}

trait BlogStorage {

  def shutdown:Unit

  def beginTx()

  def succeedTx()

  def failTx() 

  /**
   * A factory method for creating new users.
   *
   * @return newly created BlogUser with the given name and password
   */
  def createUser(name:String, password:String):BlogUser

  def userExists(named:String):Boolean

  def findUser(named:String):Option[BlogUser]

  def blogExists(named:String):Boolean

  def findBlog(named:String):Option[Blog]

}

