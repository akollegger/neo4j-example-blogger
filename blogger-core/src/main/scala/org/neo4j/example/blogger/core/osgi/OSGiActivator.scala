package org.neo4j.example.blogger.core.osgi

import java.util.Dictionary
import java.util.Properties

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext

import com.google.inject._
import com.google._
import org.ops4j.peaberry.Peaberry.osgiModule
import org.ops4j.peaberry.util.TypeLiterals.export
import org.ops4j.peaberry.Peaberry.service
import org.ops4j.peaberry.Export

import org.neo4j.example.blogger.core.internal.BlogServiceImpl
import org.neo4j.example.blogger.core.{BlogStorage, BlogService}
import org.neo4j.index.IndexService
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.index.lucene.LuceneIndexService
import org.neo4j.kernel.EmbeddedGraphDatabase
import org.neo4j.example.blogger.core.neo4j.{Neo4jFactory, Neo4jBlogStorage}


class OSGiActivator extends BundleActivator
{

  @Inject
  var blogService:Export[BlogService] = _

  override def start(bc:BundleContext) = {
    println( "STARTING blogger-core" )

    /**
     * Dependency Injection using Google Guice.
     *
     * Why Guice instead of the scala cake pattern? The cake pattern
     * is fine for composing a monolithic application, but is awkward
     * in an OSGi environment where the components have distinct
     * lifecycles and may be in other languages. With Guice annotation,
     * it's easy to move from compile-time to runtime composition.
     *
     * This is regular guice+peaberry instead of scala enhanced DSL,
     * because scalamodules is behind on scala version.
     *
     */
    val graphdb:GraphDatabaseService = new EmbeddedGraphDatabase("neo4j/blog")
    val index:IndexService = new LuceneIndexService( graphdb )

    val injector = Guice.createInjector(osgiModule(bc), new AbstractModule {
      override def configure() {
        bind(classOf[BlogStorage]).to(classOf[Neo4jBlogStorage])
        bind(classOf[GraphDatabaseService]).toInstance(graphdb);
        bind(classOf[IndexService]).toInstance(index);
        bind(classOf[Neo4jFactory]).toInstance(Neo4jBlogStorage);
        bind(export(classOf[BlogService])).toProvider(service(classOf[BlogServiceImpl]).export());
      }
    })
    injector.injectMembers(this)

  }

  override def stop(bc:BundleContext) =
  {
    println( "STOPPING blogger-core" )

    // no need to unregister our service - the OSGi framework handles it for us
  }
}