package com.mallsen.controller.user;

import com.mallsen.context.BaseContext;
import com.mallsen.entity.AddressBook;
import com.mallsen.result.Result;
import com.mallsen.service.AddressBookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/addressBook")
@Tag(name = "用户地址簿")
@Slf4j
public class AddressBookController {

    @Autowired
    private AddressBookService addressBookService;


    @PostMapping
    @Operation(summary = "新增地址")
    public Result save(@RequestBody AddressBook addressBook) {
        log.info("新增地址,参数：{}", addressBook);
        addressBookService.save(addressBook);
        return Result.success();
    }

    @GetMapping("/list")
    @Operation(summary = "查询当前登录用户的所有地址")
    public Result<List<AddressBook>> list() {
        AddressBook addressBook = new AddressBook();
        log.info("查询当前登录用户的所有地址,参数：{}", addressBook);
        addressBook.setUserId(BaseContext.getCurrentId());
        List<AddressBook> list = addressBookService.list(addressBook);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询地址")
    public Result<AddressBook> getById(@PathVariable Long id) {
        log.info("根据ID查询地址,参数：{}", id);
        AddressBook addressBook = addressBookService.getById(id);
        return Result.success(addressBook);
    }

    @PutMapping
    @Operation(summary = "根据ID修改地址")
    public Result update(@RequestBody AddressBook addressBook) {
        log.info("根据ID修改地址,参数：{}", addressBook);
        log.info("原始请求体: {}", addressBook.toString());
        addressBookService.update(addressBook);
        return Result.success();
    }

    @DeleteMapping(value = {"", "/"})
    @Operation(summary = "根据ID删除地址")
    public Result deletById(Long id) {
        log.info("根据ID删除地址,参数：{}", id);
        addressBookService.deletById(id);
        return Result.success();
    }

    @PutMapping("/default")
    @Operation(summary = "设置默认地址")
    public Result setDefault(@RequestBody AddressBook addressBook) {
        log.info("设置默认地址,参数：{}", addressBook);
        addressBookService.setDefault(addressBook);
        return Result.success();
    }

    @GetMapping("/default")
    @Operation(summary = "查询默认地址")
    public Result<AddressBook> getDefault() {
        //获取addressBook对象，设置userId和idDefault属性然后调用list方法查询，并获取第一个查询结果
        //判断查询结果，如果有数据就是默认地址，如果没有数据就是没有默认地址
        AddressBook addressBook = new AddressBook();
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(1);

        List<AddressBook> list = addressBookService.list(addressBook);

        //默认地址只有一个
        if (list != null && list.size() == 1) {
            return Result.success(list.get(0));
        }

        return Result.error("没有设置默认地址");
    }
}
