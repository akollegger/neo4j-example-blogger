package org.neo4j.example.blogger.core.model

import scalaz._
import Scalaz._

import org.neo4j.example.blogger.core.neo4j.Neo4jBlogStorage
import org.neo4j.graphdb.{Direction, Node}


object Blog {
  import Neo4jBlogStorage._

  val NAME_PROPERTY = "blog-name"
  val TITLE_PROPERTY = "blog-title"

  /**
   * Factory method for creating new blogs, associated with
   * an initial admin user.
   */
  def apply(user:BlogUser, named:String, titled:String) = {
    BlogUser.find(user.name) match {
      case Some(neo4jUser:BlogUser) => {
        val node = graphdb.createNode
        val createdBlog = new Blog(node, named, titled)
        index.index(node, NAME_PROPERTY, node.getProperty(NAME_PROPERTY))
        createdBlog.createRelationshipFrom(Some(neo4jUser), BlogUser.Relationships.CAN_ADMIN)
        createdBlog.createRelationshipFrom(Some(neo4jUser), BlogUser.Relationships.CAN_PUBLISH_TO)
        createdBlog
      }
      case _ => throw new IllegalArgumentException("user " + user + " is unknown")
    }
  }

  /**
   * Checks whether a named blog exists.
   *
   * @param named the name of the blog
   * @return true if blog with that name exists, false otherwise
   */
  def exists(named:String) = { (index.getSingleNode(NAME_PROPERTY, named) != null) }

  /**
   * Finds a blog, by name.
   *
   * @param name of blog to find
   * @return the named blog, or None if not found
   */
  def find(named:String):Option[Blog] = {
    val foundNode = index.getSingleNode(NAME_PROPERTY, named)
    foundNode match {
      case node:Node => { Some(new Blog(node)) }
      case _ => None
    }
  }
}

class Blog protected(underlyingNode:Node) extends Neo4jEntity(underlyingNode) {
  import Blog._

  def this(underlyingNode:Node, name:String, title:String) = {
    this(underlyingNode)
    this.name = name
    this.title = title
  }

  def name = underlyingNode.getProperty(NAME_PROPERTY).asInstanceOf[String]
  def name_=(newName:String) = underlyingNode.setProperty(NAME_PROPERTY, newName)
  def title = underlyingNode.getProperty(TITLE_PROPERTY).asInstanceOf[String]
  def title_=(newTitle:String) = underlyingNode.setProperty(TITLE_PROPERTY, newTitle)

  def allowAdministration(byUser: BlogUser, authorizedBy: BlogUser):Boolean = {
    authorizedBy.canAdministrate(this) && createRelationshipFrom(byUser, BlogUser.Relationships.CAN_ADMIN)
  }

  def allowPublishing(byUser: BlogUser, authorizedBy: BlogUser):Boolean = {
    authorizedBy.canAdministrate(this) && createRelationshipFrom(byUser, BlogUser.Relationships.CAN_PUBLISH_TO)
  }

  def publish(article:Article, byAuthor:BlogUser):Boolean = {
    if (articleIsReadyForPublishing(article)) {
      article.underlyingNode.createRelationshipTo(underlyingNode, Article.Relationships.PUBLISHED_IN)
      true
    } else false
  }


  /**
   * Checks whether the article is ready to be published:
   * 1. not a working copy
   * 2. is the latest version (does not have a next version)
   *
   */
  def articleIsReadyForPublishing(article:Article):Boolean = {
    !article.underlyingNode.getRelationships().exists( (r) => {
      (r.getType equals WorkingCopy.Relationships.COPY_OF) ||
        ((r.getType equals Direction.OUTGOING) && (r.getType equals Article.Relationships.NEXT_VERSION))
    })
  }

  
}