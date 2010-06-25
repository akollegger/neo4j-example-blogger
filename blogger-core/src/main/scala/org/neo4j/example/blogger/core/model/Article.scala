package org.neo4j.example.blogger.core.model

import scalaz._
import Scalaz._

import org.neo4j.graphdb.{Relationship, Direction, Node, RelationshipType}
import org.neo4j.example.blogger.core.neo4j.Neo4jBlogStorage
import collection.immutable.HashSet

object Article {
  import Neo4jBlogStorage._

  val NAME_PROPERTY = "article-name"
  val TITLE_PROPERTY = "article-title"
  val BODY_PROPERTY = "body-title"

  object Relationships extends Enumeration {
    type Relationships = RelationshipType
    val NEXT_VERSION =  new RelationshipType { def name = "next-version" }
    val PUBLISHED_IN =  new RelationshipType { def name = "published-in" }
  }

  /**
   * Factory method for creating new articles, associated with
   * an owner.
   */
  def apply(user:BlogUser, named:String, titled:String) = {
    BlogUser.find(user.name) match {
      case Some(neo4jUser:BlogUser) => {
        val node = graphdb.createNode
        val createdArticle = new Article(node, named, titled)
        createdArticle.createRelationshipFrom(neo4jUser, BlogUser.Relationships.IS_AUTHOR)
        createdArticle.createRelationshipFrom(neo4jUser, BlogUser.Relationships.IS_OWNER)
        createdArticle
      }
      case _ => throw new IllegalArgumentException("user " + user + " is unknown")
    }
  }
}

class Article protected(underlyingNode:Node) extends Neo4jEntity(underlyingNode) {
  import Article._

  def this(underlyingNode:Node, name:String, title:String) = {
    this(underlyingNode)
    setName(name)
    setTitle(title)
  }

  def name = underlyingNode.getProperty(NAME_PROPERTY).asInstanceOf[String]
  protected def setName(newName:String) = underlyingNode.setProperty(NAME_PROPERTY, newName)
  def title = underlyingNode.getProperty(TITLE_PROPERTY).asInstanceOf[String]
  protected def setTitle(newTitle:String) = underlyingNode.setProperty(TITLE_PROPERTY, newTitle)
  def body = {
    if (underlyingNode.hasProperty(BODY_PROPERTY)) {
      underlyingNode.getProperty(BODY_PROPERTY).asInstanceOf[String]
    } else ""
  }
  protected def setBody(newBody:String) = underlyingNode.setProperty(BODY_PROPERTY, newBody)

  def nextVersion = {
    val nextVersionRelationship = underlyingNode.getSingleRelationship(Article.Relationships.NEXT_VERSION, Direction.OUTGOING)
    if (nextVersionRelationship != null) {
      Some(new Article(nextVersionRelationship.getEndNode))
    } else {
      None
    }
  }

  def nextVersion_=(newNextVersion:Some[Article]) = {
    underlyingNode.createRelationshipTo(newNextVersion.get.underlyingNode, Article.Relationships.NEXT_VERSION)
    newNextVersion
  }

  def allowAdministration(byUser: BlogUser, authorizedBy: BlogUser):Boolean = {
    authorizedBy.owns(this) && createRelationshipFrom(byUser, BlogUser.Relationships.CAN_ADMIN)
  }

  def allowAuthoring(byUser: BlogUser, authorizedBy: BlogUser):Boolean = {
    authorizedBy.owns(this) && addAuthor(byUser.underlyingNode)
  }

  def publishedIn:Set[Blog] = {
    // ABKTODO: there has got to be a nicer way to write this
    // generate an iterable, convert to a sequence, then make that into a set? ugh.
    val blogs = for (r <- underlyingNode.getRelationships(Article.Relationships.PUBLISHED_IN)) yield {
      new Blog(r.getEndNode) 
    }
    Set[Blog](blogs toSeq : _ *)
  }

  def isPublished:Boolean = {
    !underlyingNode.getRelationships(Article.Relationships.PUBLISHED_IN).isEmpty
  }

  def isLatest:Boolean = {
    underlyingNode.getRelationships(Article.Relationships.NEXT_VERSION, Direction.OUTGOING).isEmpty
  }

  def addAuthor(secondAuthor:BlogUser, authorizedBy:BlogUser):Boolean = {
    !isPublished && authorizedBy.owns(this) && addAuthor(secondAuthor.underlyingNode)
  }

  def removeAuthor(secondAuthor:BlogUser, authorizedBy:BlogUser):Boolean = {
    authorizedBy.owns(this) && removeAuthor(secondAuthor.underlyingNode)
  }

  def forward:Iterator[Article] = new NextArticleTraversal(this)

  // ABKTODO: consider using a traverser
  protected class NextArticleTraversal(initial:Article) extends Iterator[Article] {
    private var current:Article = initial
    def hasNext:Boolean = !current.isLatest
    def next():Article = { current = current.nextVersion.get; current }
  }

  private[neo4j] def addAuthor(authorNode:Node):Boolean = {
    try {
      authorNode.createRelationshipTo(underlyingNode, BlogUser.Relationships.IS_AUTHOR)
      true
    } catch {
      case e => false
    }
  }

  private[neo4j] def removeAuthor(authorNode:Node):Boolean = {
    try {
      underlyingNode.getRelationships(BlogUser.Relationships.IS_AUTHOR).find( (r) => {
        r.getStartNode.equals(authorNode)
      }) match {
        case Some(r) => r.delete; true
        case _ => false
      }
    } catch {
      case e => false
    }
  }

  override def toString = name
  
}

object WorkingCopy {
  import Neo4jBlogStorage._

  object Relationships extends Enumeration {
    type Relationships = RelationshipType
    val COPY_OF =  new RelationshipType { def name = "copy-of" }
  }

  def apply(originalArticle:Article, editingUser:BlogUser) = {
    val copy = new WorkingCopy(graphdb.createNode)
    // ABKTODO: copy all properties with introspection instead?
    copy.name = originalArticle.name
    copy.title = originalArticle.title
    copy.body = originalArticle.body

    // connect the working copy to the user
    editingUser.underlyingNode.createRelationshipTo(copy.underlyingNode, BlogUser.Relationships.IS_OWNER)
    val wcLink = editingUser.underlyingNode.createRelationshipTo(copy.underlyingNode, BlogUser.Relationships.WORKING_COPY)
    wcLink.setProperty(Article.NAME_PROPERTY, copy.name)
    copy.copyOf=originalArticle
    copy
  }

}


/**
 * A mutable article.
 */
class WorkingCopy protected(node:Node) extends Article(node) {

  def name_=(newName:String) = setName(newName)
  def title_=(newTitle:String) = setTitle(newTitle)
  def body_=(newBody:String) = setBody(newBody)

  def copyOf = {
    val copyOfRelationship = underlyingNode.getSingleRelationship(WorkingCopy.Relationships.COPY_OF, Direction.OUTGOING)
    if (copyOfRelationship != null) {
      new Article(copyOfRelationship.getEndNode)
    } else {
      throw new IllegalStateException("WorkingCopy does not have reference to original article")
    }
  }

  def copyOf_=(newCopyOf:Article) = {
    underlyingNode.createRelationshipTo(newCopyOf.underlyingNode, WorkingCopy.Relationships.COPY_OF)
  }

  def cancel:Boolean = {
    try {
      delete
      true
    } catch {
      case e => false
    }
  }

  def save:Option[Article] = {
    try {
      val originalArticle = copyOf
      if (originalArticle.isLatest) {
        var wcLink = underlyingNode.getSingleRelationship(BlogUser.Relationships.WORKING_COPY, Direction.INCOMING)
        var copyOfLink = underlyingNode.getSingleRelationship(WorkingCopy.Relationships.COPY_OF, Direction.OUTGOING)
        var author = wcLink.getStartNode
        originalArticle.removeAuthor(author)
        addAuthor(author)
        wcLink.delete
        copyOfLink.delete
        originalArticle.nextVersion = Some(this.asInstanceOf[Article])
      } else None
    } catch {
      case e => None
    }
  }

  def merge:WorkingCopy = {
    for (article <- copyOf.forward) {
      var copyOfLink = underlyingNode.getSingleRelationship(WorkingCopy.Relationships.COPY_OF, Direction.OUTGOING)
      copyOfLink.delete
      // blind copy values over instead of diffing
      name = article.name
      title = article.title
      body = article.body
      copyOf = article
    }
    this
  }
}
