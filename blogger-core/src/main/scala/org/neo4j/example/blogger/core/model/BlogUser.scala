package org.neo4j.example.blogger.core.model

import scalaz._
import Scalaz._
import org.neo4j.graphdb.{Direction, Relationship, Node, RelationshipType}

// ABKTODO: annoying circlular package dependencies. re-organize this
import org.neo4j.example.blogger.core.neo4j.Neo4jBlogStorage

object BlogUser {
  import Neo4jBlogStorage._

  val NAME_PROPERTY = "username"
  val PASSWORD_PROPERTY = "password"

  object Relationships extends Enumeration {
    type Relationships = RelationshipType
    val CAN_ADMIN =  new RelationshipType { def name = "can-admin" }
    val CAN_PUBLISH_TO =  new RelationshipType { def name = "can-publish-to" }
    val IS_OWNER =  new RelationshipType { def name = "is-owner" }
    val IS_AUTHOR =  new RelationshipType { def name = "is-author" }
    val WORKING_COPY =  new RelationshipType { def name = "working-copy" }
  }

  def apply(name:String, password:String) = {
    val node = graphdb.createNode
    val createdUser = new BlogUser(node, name, password)
    index.index(node, NAME_PROPERTY, node.getProperty(NAME_PROPERTY))
    createdUser
  }

  def exists(named:String) = { (index.getSingleNode(NAME_PROPERTY, named) != null) }

  def find(named:String) = {
    val foundNode = index.getSingleNode(NAME_PROPERTY, named)
    foundNode match {
      case node:Node => { Some(new BlogUser(node)) }
      case _ => None
    }
  }

  implicit def userToNeoUser(user:BlogUser):Option[BlogUser] = find(user.name)
}

class BlogUser(underlyingNode:Node) extends Neo4jEntity(underlyingNode) {
  import BlogUser._

  def this(underlyingNode:Node, name:String, password:String) = {
    this(underlyingNode)
    this.name = name
    this.password = password
  }

  def name = underlyingNode.getProperty(NAME_PROPERTY).asInstanceOf[String]
  def name_=(newName:String) = underlyingNode.setProperty(NAME_PROPERTY, newName)

  def password = underlyingNode.getProperty(PASSWORD_PROPERTY).asInstanceOf[String]
  def password_=(newPassword:String) = underlyingNode.setProperty(PASSWORD_PROPERTY, newPassword)

  def canAdministrate(blog:Blog):Boolean = hasRelationshipTo(blog, BlogUser.Relationships.CAN_ADMIN)
  def canPublishTo(blog:Blog):Boolean = hasRelationshipTo(blog, BlogUser.Relationships.CAN_PUBLISH_TO)

  def owns(article:Article):Boolean = hasRelationshipTo(article, BlogUser.Relationships.IS_OWNER)

  def canAuthor(article:Article):Boolean = hasRelationshipTo(article, BlogUser.Relationships.IS_AUTHOR)

  def createBlog(named: String, titled: String) = {
    if (!Blog.exists(named)) {
      try {
        Some(Blog(this, named, titled))
      } catch {
        case e:Throwable => {
          System.err.println("blog creation failed, because:" + e)
          None
        }
      }
    } else {
      None
    }
  }

  def createArticle(named: String, titled: String) = {
    if (!findArticle(named).isDefined) {
      try {
        Some(Article(this, named, titled))
      } catch {
        case e:Throwable => {
          System.err.println("article creation failed, because:" + e)
          None
        }
      }
    } else {
      None
    }
  }

  def findArticle(named:String) = {
    underlyingNode.getRelationships(BlogUser.Relationships.IS_AUTHOR).find( (r) => {
      r.getEndNode.getProperty(Article.NAME_PROPERTY).equals(named)
    }) match {
      case Some(relationship) => {
        Some(new Article(relationship.getEndNode))
      }
      case _ => None
    }
  }

  def allArticles:Set[Article] = {
    val articles = for (r <- underlyingNode.getRelationships(BlogUser.Relationships.IS_AUTHOR)) yield {
      new Article(r.getEndNode)
    }
    Set[Article](articles toSeq : _ *)
  }

  def edit(article:Article):Option[WorkingCopy] = {
    if (!article.isPublished) {
      // check for existing working copy
      underlyingNode.getRelationships(BlogUser.Relationships.WORKING_COPY).find( (r) => {
        r.getProperty(Article.NAME_PROPERTY).equals(article.name)
      }) match {
        case Some(relationship:Relationship) => {
          Some(new WorkingCopy(relationship.getEndNode))
        }
        case _ => {
          Some(WorkingCopy(article, this))
        }
      }
    } else None
  }

}
