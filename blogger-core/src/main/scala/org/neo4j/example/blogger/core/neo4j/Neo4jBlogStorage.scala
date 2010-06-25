package org.neo4j.example.blogger.core.neo4j

import org.neo4j.example.blogger.core.BlogStorage
import java.lang.String
import org.neo4j.kernel.EmbeddedGraphDatabase
import org.neo4j.index.lucene.LuceneIndexService
import org.neo4j.example.blogger.core.model.{Article, Blog, BlogUser}
import com.google.inject.Inject
import org.neo4j.graphdb.{GraphDatabaseService, Transaction}
import org.neo4j.index.IndexService

trait Neo4jFactory {
  
}

object Neo4jBlogStorage extends Neo4jFactory {
  
  @Inject var graphdb:GraphDatabaseService = null; // new EmbeddedGraphDatabase("neo4j/blog")
  @Inject var index:IndexService = null; // = new LuceneIndexService( graphdb )
  
}
  
class Neo4jBlogStorage extends BlogStorage {

  import Neo4jBlogStorage._

  var currentTx:Transaction = _

  def shutdown = {
    graphdb.shutdown
  }

  def beginTx() {
    currentTx = Neo4jBlogStorage.graphdb.beginTx
  }

  def succeedTx() {
    currentTx.success
  }

  def failTx() {
    currentTx.failure
  }
  
  def createUser(name: String, password: String):BlogUser = {
    BlogUser(name, password)
  }

  def userExists(named:String) = {
    BlogUser.exists(named)
  }

  def findUser(named:String) = {
    BlogUser.find(named)
  }

  def createBlogFor(user: BlogUser, named: String, titled:String): Blog = {
    Blog(user, named, titled)
  }

  def blogExists(named: String): Boolean = {
    Blog.exists(named)
  }

  def findBlog(named:String) = {
    Blog.find(named)
  }

}
