package com.geccocrawler.gecco.spider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;

import com.geccocrawler.gecco.annotation.Gecco;
import com.geccocrawler.gecco.downloader.DownloaderAOPFactory;
import com.geccocrawler.gecco.pipeline.Pipeline;
import com.geccocrawler.gecco.pipeline.DefaultPipelineFactory;
import com.geccocrawler.gecco.pipeline.PipelineFactory;
import com.geccocrawler.gecco.request.HttpRequest;
import com.geccocrawler.gecco.spider.render.RenderFactory;
import com.geccocrawler.gecco.spider.render.RenderType;
import com.geccocrawler.gecco.utils.ReflectUtils;
import com.geccocrawler.gecco.utils.UrlMatcher;

/**
 * SpiderBean是爬虫渲染的JavaBean的统一接口类，所有Bean均继承该接口。SpiderBeanFactroy会根据请求的url地址，
 * 匹配相应的SpiderBean，同时生成该SpiderBean的上下文SpiderBeanContext.
 * SpiderBeanContext包括需要改SpiderBean的渲染类
 * （目前支持HTML、JSON两种Bean的渲染方式）、下载前处理类、下载后处理类以及渲染完成后对SpiderBean的后续处理Pipeline。
 * 
 * @author huchengyi
 *
 */
public class SpiderBeanFactory {
	
	private Map<String, Class<? extends SpiderBean>> spiderBeans;
	
	private Map<String, SpiderBeanContext> spiderBeanContexts;
	
	private DownloaderAOPFactory downloaderAOPFactory;
	
	private RenderFactory renderFactory;
	
	private PipelineFactory pipelineFactory;
	
	public SpiderBeanFactory(String classPath) {
		this(classPath, null);
	}
	
	public SpiderBeanFactory(String classPath, PipelineFactory pipelineFactory) {
		Reflections reflections = null;
		if(StringUtils.isNotEmpty(classPath)) {
			reflections = new Reflections("com.geccocrawler.gecco", classPath);
		} else {
			reflections = new Reflections("com.geccocrawler.gecco");
		}
		this.downloaderAOPFactory = new DownloaderAOPFactory(reflections);
		this.renderFactory = new RenderFactory(reflections);
		if(pipelineFactory != null) {
			this.pipelineFactory = pipelineFactory;
		} else {
			this.pipelineFactory = new DefaultPipelineFactory(reflections);
		}
		this.spiderBeans = new HashMap<String, Class<? extends SpiderBean>>();
		this.spiderBeanContexts = new HashMap<String, SpiderBeanContext>();
		loadSpiderBean(reflections);
	}
	
	private void loadSpiderBean(Reflections reflections) {
		Set<Class<?>> spiderBeanClasses = reflections.getTypesAnnotatedWith(Gecco.class);
		for(Class<?> spiderBeanClass : spiderBeanClasses) {
			Gecco gecco = (Gecco)spiderBeanClass.getAnnotation(Gecco.class);
			String matchUrl = gecco.matchUrl();
			try {
				//SpiderBean spider = (SpiderBean)spiderBeanClass.newInstance();
				//判断是不是SpiderBeanClass????
				spiderBeans.put(matchUrl, (Class<? extends SpiderBean>)spiderBeanClass);
				SpiderBeanContext context = initContext(spiderBeanClass);
				spiderBeanContexts.put(spiderBeanClass.getName(), context);
			} catch(Exception ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public Class<? extends SpiderBean> matchSpider(HttpRequest request) {
		String url = request.getUrl();
		for(Map.Entry<String, Class<? extends SpiderBean>> entrys : spiderBeans.entrySet()) {
			String urlPattern = entrys.getKey();
			Map<String, String> params = UrlMatcher.match(url, urlPattern);
			if(params != null) {
				request.setParameters(params);
				Class<? extends SpiderBean> spider = entrys.getValue();
				return spider;
			}
		}
		return null;
		
	}
	
	public SpiderBeanContext getContext(Class<? extends SpiderBean> spider) {
		return spiderBeanContexts.get(spider.getName());
	}
	
	private SpiderBeanContext initContext(Class<?> spiderBeanClass) {
		SpiderBeanContext context = new SpiderBeanContext();
		
		String spiderBeanName = spiderBeanClass.getName();
		downloadContext(context, spiderBeanName);

		renderContext(context, spiderBeanClass);
		
		Gecco gecco = spiderBeanClass.getAnnotation(Gecco.class);
		String[] pipelineNames = gecco.pipelines();
		pipelineContext(context, pipelineNames);
		
		return context;
	}
	
	private void downloadContext(SpiderBeanContext context, String geccoName) {
		context.setBeforeDownload(downloaderAOPFactory.getBefore(geccoName));
		context.setAfterDownload(downloaderAOPFactory.getAfter(geccoName));
	}
	
	private void renderContext(SpiderBeanContext context, Class<?> spiderBeanClass) {
		RenderType renderType = RenderType.HTML;
		if(ReflectUtils.haveSuperType(spiderBeanClass, JsonBean.class)) {
			renderType = RenderType.JSON;
		}
		context.setRender(renderFactory.getRender(renderType));
	}
	
	private void pipelineContext(SpiderBeanContext context, String[] pipelineNames) {
		if(pipelineNames != null && pipelineNames.length > 0) {
			List<Pipeline> pipelines = new ArrayList<Pipeline>();
			for(String pipelineName : pipelineNames) {
				if(StringUtils.isEmpty(pipelineName)) {
					continue;
				}
				Pipeline pipeline = pipelineFactory.getPipeline(pipelineName);
				if(pipeline != null) {
					pipelines.add(pipeline);
				}
			}
			context.setPipelines(pipelines);
		}
	}

	public DownloaderAOPFactory getDownloaderAOPFactory() {
		return downloaderAOPFactory;
	}

	public RenderFactory getRenderFactory() {
		return renderFactory;
	}

	public PipelineFactory getPipelineFactory() {
		return pipelineFactory;
	}

}
