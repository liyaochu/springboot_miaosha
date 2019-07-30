package com.lyc;

import com.lyc.dao.UserDOMapper;
import com.lyc.dataobject.UserDO;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Hello world!
 *
 */
@SpringBootApplication(scanBasePackages = {"com.lyc"})
@RestController
@MapperScan("com.lyc.dao")
public class App 
{

    @Autowired
    private UserDOMapper userDOMapper;
    @RequestMapping("/")
    public String home(){
        UserDO userDO = userDOMapper.selectByPrimaryKey(1);
        if (Objects.nonNull(userDO)){
            return userDO.getName() ;
        }else
        {
            return "helloworld" ;
        }


    }

    public static void main( String[] args )
    {
        System.out.println( "Hello World!" );

        SpringApplication.run(App.class,args);
    }
}
