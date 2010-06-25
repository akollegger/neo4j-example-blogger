package org.neo4j.example.blogger.core.internal

import org.neo4j.example.blogger.core.{BlogStorage, BlogService}
import com.google.inject.Inject
import java.lang.String
import org.neo4j.example.blogger.core.model.{Blog, BlogUser}

class BlogServiceImpl
    extends BlogService
{
  @Inject
  var blogStorage:BlogStorage = _

  def shutdown = {
    blogStorage.shutdown
  }

  def beginTx() = blogStorage.beginTx 

  def succeedTx() = blogStorage.succeedTx

  def failTx() = blogStorage.failTx

  /**
   * Create a user account with a name and initial password.
   */
  def createUser(named: String, withPassword: String):Option[BlogUser] = {
    if (!blogStorage.userExists(named)) {
      Some(blogStorage.createUser(named, withPassword))
    } else {
      None
    }
  }

  def userExists(named:String):Boolean = {
    return blogStorage.userExists(named)
  }

  def signIn(username:String, password:String):Option[BlogUser] = {
    val foundUser = blogStorage.findUser(username)
    foundUser match {
      case Some(user) if (user.password == password) => foundUser
      case _ => None
    }

  }


  def findBlog(named: String): Option[Blog] = { blogStorage.findBlog(named)}

}

