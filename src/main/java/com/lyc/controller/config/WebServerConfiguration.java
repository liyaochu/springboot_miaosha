package com.lyc.controller.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;

import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * @program: springboot_miaosha
 * @description:当spring容器类没有TomcatEmbeddedServletContainerFactory这个bean时,就会把此bean加载进来
 * 是用来定制化tomcat容器开发,springboot 提供了 WebServerFactoryCustomizer<ConfigurableWebServerFactory>定制化tomcat
 * @author: Jhon_Li
 * @create: 2019-07-28 21:03
 **/
@Component
public class WebServerConfiguration  implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {
    @Override
    public void customize(ConfigurableWebServerFactory factory) {

        //使用对应的工厂类提供给我们的接口定制化tomcat connect
        ((TomcatServletWebServerFactory)factory).addConnectorCustomizers(new TomcatConnectorCustomizer() {
            @Override
            public void customize(Connector connector) {
                Http11NioProtocol protocol = (Http11NioProtocol)connector.getProtocolHandler();

                //定制化参数keepalivetimeout.设置30秒内没有请求,服务端断开keeplivetimeout连接
                protocol.setKeepAliveTimeout(30000);
                //当客户端发送10000个请求就自动断开定制化参数keepalive连接
                protocol.setMaxKeepAliveRequests(10000);
            }
        });
    }
}
