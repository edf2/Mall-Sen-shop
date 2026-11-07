package com.mallsen.service.impl;

import com.mallsen.context.BaseContext;
import com.mallsen.entity.AddressBook;
import com.mallsen.mapper.AddressBookMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressBookServiceImplTest {

    @Mock
    private AddressBookMapper addressBookMapper;

    @InjectMocks
    private AddressBookServiceImpl addressBookService;

    private AddressBook addressBook;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(1L);

        addressBook = AddressBook.builder()
                .id(10L)
                .userId(1L)
                .consignee("张三")
                .phone("13800138000")
                .detail("北京市朝阳区某街道")
                .isDefault(1)
                .build();
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void list_shouldReturnAddressList() {
        when(addressBookMapper.list(addressBook)).thenReturn(List.of(addressBook));

        List<AddressBook> result = addressBookService.list(addressBook);

        assertEquals(1, result.size());
        assertEquals("张三", result.get(0).getConsignee());
    }

    @Test
    void save_shouldSetUserIdAndIsDefault() {
        AddressBook input = new AddressBook();
        input.setConsignee("李四");
        input.setDetail("上海市浦东新区");

        addressBookService.save(input);

        verify(addressBookMapper).save(argThat(a ->
                a.getUserId().equals(1L) &&
                a.getIsDefault() == 0 &&
                a.getConsignee().equals("李四")
        ));
    }

    @Test
    void update_shouldCallMapperUpdate() {
        addressBookService.update(addressBook);

        verify(addressBookMapper).update(addressBook);
    }

    @Test
    void deletById_shouldCallMapperDelete() {
        addressBookService.deletById(10L);

        verify(addressBookMapper).deletById(10L);
    }

    @Test
    void getById_shouldReturnAddress() {
        when(addressBookMapper.getById(10L)).thenReturn(addressBook);

        AddressBook result = addressBookService.getById(10L);

        assertNotNull(result);
        assertEquals("张三", result.getConsignee());
    }

    @Test
    void setDefault_shouldUnsetOthersThenSetCurrent() {
        AddressBook input = new AddressBook();
        input.setId(10L);

        final AddressBook[] capturedSetDefault = new AddressBook[1];
        doAnswer(invocation -> {
            AddressBook arg = invocation.getArgument(0);
            capturedSetDefault[0] = AddressBook.builder()
                    .id(arg.getId())
                    .userId(arg.getUserId())
                    .isDefault(arg.getIsDefault())
                    .build();
            return null;
        }).when(addressBookMapper).setDefault(any(AddressBook.class));

        addressBookService.setDefault(input);

        assertEquals(0, capturedSetDefault[0].getIsDefault().intValue());
        assertEquals(1L, capturedSetDefault[0].getUserId().longValue());

        verify(addressBookMapper).update(argThat(a ->
                a.getId().equals(10L) &&
                a.getIsDefault() == 1
        ));
    }
}
